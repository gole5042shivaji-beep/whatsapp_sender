using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Management;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Web.Script.Serialization;

namespace GccSender
{
    internal class LicenseResult
    {
        public bool Ok;
        public string Code = "";
        public string Message = "";
    }

    /// <summary>Activation: Google Sheet मधील "Sender" टॅब + स्थानिक (DPAPI) साठवण.</summary>
    internal class LicenseManager
    {
        // ===== आपली Apps Script Web App URL =====
        private const string WebAppUrl =
            "https://script.google.com/macros/s/AKfycbynrzXYaxmR7xuIwp5bWbax300nUnCfNszVITFHHqC_I8I5qaUI50ZMe1UzttY1y8Ai/exec";
        private const string Salt = "whatsapp-sender-win-v1-Pz4k";
        private static readonly TimeSpan MaxOffline = TimeSpan.FromDays(30);

        private readonly string _file;
        private Dictionary<string, object> _d = new Dictionary<string, object>();
        public string MachineId { get; private set; }

        public LicenseManager()
        {
            string dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "WhatsApp Sender");
            Directory.CreateDirectory(dir);
            _file = Path.Combine(dir, "lic.dat");
            MachineId = ComputeMachineId();
            Load();
        }

        // ---------------------------------------------------------------- Machine ID
        private static string ComputeMachineId()
        {
            try
            {
                string combined = Wmi("Win32_Processor", "ProcessorId") + "|" + DiskSerial() + "|" + Wmi("Win32_BaseBoard", "SerialNumber");
                using (var sha = SHA256.Create())
                {
                    byte[] h = sha.ComputeHash(Encoding.UTF8.GetBytes(combined));
                    var sb = new StringBuilder();
                    for (int i = 0; i < 10; i++) sb.Append(h[i].ToString("X2"));
                    string s = sb.ToString();
                    return "WIN-" + s.Substring(0, 5) + "-" + s.Substring(5, 5) + "-" + s.Substring(10, 5) + "-" + s.Substring(15, 5);
                }
            }
            catch { return "WIN-UNKNOWN"; }
        }

        private static string Wmi(string cls, string prop)
        {
            try
            {
                using (var s = new ManagementObjectSearcher("SELECT " + prop + " FROM " + cls))
                    foreach (ManagementObject o in s.Get())
                    {
                        object v = o[prop];
                        if (v != null) return v.ToString();
                    }
            }
            catch { }
            return "";
        }

        private static string DiskSerial()
        {
            try
            {
                using (var s = new ManagementObjectSearcher("SELECT SerialNumber FROM Win32_DiskDrive WHERE MediaType = 'Fixed hard disk media'"))
                    foreach (ManagementObject o in s.Get())
                    {
                        object v = o["SerialNumber"];
                        if (v != null) return v.ToString();
                    }
            }
            catch { }
            return "";
        }

        // ---------------------------------------------------------------- स्थानिक साठवण
        private string Sign(string key, string expiry, long lastCheck)
        {
            using (var sha = SHA256.Create())
            {
                byte[] h = sha.ComputeHash(Encoding.UTF8.GetBytes(Salt + "|" + key + "|" + expiry + "|" + lastCheck + "|" + MachineId));
                var sb = new StringBuilder();
                foreach (byte b in h) sb.Append(b.ToString("x2"));
                return sb.ToString();
            }
        }

        private static byte[] Entropy { get { return Encoding.UTF8.GetBytes(Salt); } }

        private void Load()
        {
            try
            {
                if (!File.Exists(_file)) return;
                byte[] raw = ProtectedData.Unprotect(File.ReadAllBytes(_file), Entropy, DataProtectionScope.CurrentUser);
                var o = new JavaScriptSerializer().DeserializeObject(Encoding.UTF8.GetString(raw)) as Dictionary<string, object>;
                if (o != null) _d = o;
            }
            catch { _d = new Dictionary<string, object>(); }
        }

        private void SaveFile()
        {
            string json = new JavaScriptSerializer().Serialize(_d);
            File.WriteAllBytes(_file, ProtectedData.Protect(Encoding.UTF8.GetBytes(json), Entropy, DataProtectionScope.CurrentUser));
        }

        private string S(string k) { object v; return _d.TryGetValue(k, out v) && v != null ? v.ToString() : ""; }
        private long L(string k) { long r; return long.TryParse(S(k), out r) ? r : 0; }
        private static long NowMs() { return (long)(DateTime.UtcNow - new DateTime(1970, 1, 1)).TotalMilliseconds; }
        private static string Today() { return DateTime.Now.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture); }

        private void SaveLocal(string key, string name, string expiryIso)
        {
            long now = NowMs();
            _d = new Dictionary<string, object>
            {
                { "key", key }, { "name", name }, { "expiry", expiryIso },
                { "lastCheck", now }, { "lastRun", now }, { "sig", Sign(key, expiryIso, now) }
            };
            SaveFile();
        }

        public void Clear()
        {
            _d = new Dictionary<string, object>();
            try { if (File.Exists(_file)) File.Delete(_file); } catch { }
        }

        public string Name { get { return S("name"); } }
        public string ExpiryDisplay
        {
            get
            {
                DateTime d;
                return DateTime.TryParseExact(S("expiry"), "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out d)
                    ? d.ToString("dd-MM-yyyy") : "";
            }
        }
        public string SavedKey { get { return S("key"); } }

        /// <summary>0 ठीक, 1 ऑनलाइन तपासणी आवश्यक, 2 Activation नाही/खराब, 3 मुदत संपली</summary>
        public int LocalState()
        {
            string key = S("key"), exp = S("expiry");
            if (key.Length == 0 || exp.Length == 0) return 2;
            if (Sign(key, exp, L("lastCheck")) != S("sig")) return 2;
            if (string.CompareOrdinal(Today(), exp) > 0) return 3;
            long now = NowMs();
            if (now < L("lastRun") - 86400000L) return 1;                       // घड्याळ मागे केले असावे
            if (now - L("lastCheck") > (long)MaxOffline.TotalMilliseconds) return 1;
            return 0;
        }

        public void TouchRun()
        {
            if (S("key").Length == 0) return;
            _d["lastRun"] = NowMs();
            try { SaveFile(); } catch { }
        }

        // ---------------------------------------------------------------- सर्व्हर
        private Dictionary<string, object> CallServer(string key)
        {
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
            var ser = new JavaScriptSerializer();
            string json = ser.Serialize(new Dictionary<string, object>
            {
                { "action", "senderActivate" }, { "licenseKey", key }, { "machineId", MachineId }, { "platform", "windows" }
            });
            var req = (HttpWebRequest)WebRequest.Create(WebAppUrl);
            req.Method = "POST";
            req.ContentType = "application/json";
            req.Timeout = 20000;
            byte[] data = Encoding.UTF8.GetBytes(json);
            req.ContentLength = data.Length;
            using (var st = req.GetRequestStream()) st.Write(data, 0, data.Length);
            string body;
            try
            {
                using (var resp = (HttpWebResponse)req.GetResponse())
                using (var rd = new StreamReader(resp.GetResponseStream(), Encoding.UTF8))
                    body = rd.ReadToEnd();
            }
            catch (WebException wex)
            {
                if (wex.Response == null) throw;
                using (var rd = new StreamReader(wex.Response.GetResponseStream(), Encoding.UTF8))
                    body = rd.ReadToEnd();
            }
            return ser.DeserializeObject(body) as Dictionary<string, object>;
        }

        private static string G(Dictionary<string, object> d, string k)
        {
            object v; return d != null && d.TryGetValue(k, out v) && v != null ? v.ToString() : "";
        }

        private static string ToIso(string ddMMyyyy)
        {
            DateTime d;
            return DateTime.TryParseExact(ddMMyyyy, "dd-MM-yyyy", CultureInfo.InvariantCulture, DateTimeStyles.None, out d)
                ? d.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture) : ddMMyyyy;
        }

        /// <summary>नवीन Key ने Activation (UI थ्रेड वर नको — Task.Run मध्ये चालवा)</summary>
        public LicenseResult Activate(string key)
        {
            try
            {
                var r = CallServer(key);
                if (r == null) return new LicenseResult { Message = "सर्व्हरचे उत्तर समजले नाही." };
                if (G(r, "success").ToLower() == "true")
                {
                    SaveLocal(key, G(r, "name"), ToIso(G(r, "expiryDate")));
                    return new LicenseResult { Ok = true };
                }
                return new LicenseResult { Code = G(r, "code"), Message = G(r, "message").Length > 0 ? G(r, "message") : "Activation झाले नाही." };
            }
            catch (Exception)
            {
                return new LicenseResult { Code = "NET", Message = "इंटरनेट/सर्व्हर समस्या. इंटरनेट चालू करून पुन्हा प्रयत्न करा." };
            }
        }

        /// <summary>ॲप सुरू झाल्यावर पार्श्वभूमीत तपासणी. null = ठीक/ऑफलाइन, नाहीतर लॉक करण्याचा संदेश.</summary>
        public string Recheck()
        {
            string key = S("key");
            if (key.Length == 0) return null;
            try
            {
                var r = CallServer(key);
                if (r == null) return null;
                if (G(r, "success").ToLower() == "true")
                {
                    SaveLocal(key, G(r, "name"), ToIso(G(r, "expiryDate")));
                    return null;
                }
                string code = G(r, "code"), msg = G(r, "message");
                if (code == "REVOKED" || code == "INVALID" || code == "DEVICE_LIMIT" || code == "EXPIRED")
                {
                    Clear();
                    return msg.Length > 0 ? msg : "Activation अवैध आहे.";
                }
            }
            catch { /* ऑफलाइन — स्थानिक Activation वर चालू */ }
            return null;
        }
    }
}
