using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace GccSender
{
    internal class MainForm : Form
    {
        private readonly LicenseManager _lic;
        private readonly WebView2 _wv = new WebView2();
        private byte[] _html;
        private CoreWebView2Environment _env;
        public bool RelockRequested;
        public string RelockMessage;

        public MainForm(LicenseManager lic)
        {
            _lic = lic;
            Text = "WhatsApp Message Sender — मुदत: " + lic.ExpiryDisplay;
            Width = 1100; Height = 820;
            StartPosition = FormStartPosition.CenterScreen;
            _wv.Dock = DockStyle.Fill;
            Controls.Add(_wv);
            Load += async (s, e) => await InitAsync();
        }

        private static byte[] LoadHtml()
        {
            using (var st = Assembly.GetExecutingAssembly().GetManifestResourceStream("index.html.enc"))
            using (var ms = new MemoryStream())
            {
                st.CopyTo(ms);
                return FileCrypt.Decrypt(ms.ToArray());
            }
        }

        private async Task InitAsync()
        {
            try
            {
                _html = LoadHtml();
            }
            catch
            {
                MessageBox.Show("ॲप फाइल खराब आहे. कृपया पुन्हा Install करा.");
                Close();
                return;
            }

            try
            {
                string ud = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                                         "GCC-TBC Sender", "WebView2");
                _env = await CoreWebView2Environment.CreateAsync(null, ud);
                await _wv.EnsureCoreWebView2Async(_env);
            }
            catch (Exception ex)
            {
                MessageBox.Show("Microsoft Edge WebView2 Runtime सापडले नाही.\n\n" +
                                "ते येथून Install करा: https://go.microsoft.com/fwlink/p/?LinkId=2124703\n\n" + ex.Message,
                                "WebView2 आवश्यक आहे");
                Close();
                return;
            }

            CoreWebView2 core = _wv.CoreWebView2;
            core.Settings.AreDevToolsEnabled = false;
            core.Settings.IsStatusBarEnabled = false;
            await core.AddScriptToExecuteOnDocumentCreatedAsync("window.__GCC={platform:'windows'};");

            core.AddWebResourceRequestedFilter("https://sender.app/*", CoreWebView2WebResourceContext.All);
            core.WebResourceRequested += OnResource;
            core.NewWindowRequested += (s, e) => { e.Handled = true; OpenExternal(e.Uri); };
            core.NavigationStarting += (s, e) =>
            {
                string u = e.Uri ?? "";
                if (u.StartsWith("https://sender.app/", StringComparison.OrdinalIgnoreCase) ||
                    u.StartsWith("about:", StringComparison.OrdinalIgnoreCase) ||
                    u.StartsWith("blob:", StringComparison.OrdinalIgnoreCase)) return;
                e.Cancel = true;
                OpenExternal(u);
            };
            core.PermissionRequested += (s, e) =>
            {
                if (e.PermissionKind == CoreWebView2PermissionKind.ClipboardRead)
                    e.State = CoreWebView2PermissionState.Allow;
            };
            core.Navigate("https://sender.app/index.html");

            // पार्श्वभूमीत Activation ची ऑनलाइन तपासणी
            string res = await Task.Run(() => _lic.Recheck());
            if (res != null)
            {
                RelockRequested = true;
                RelockMessage = res;
                Close();
            }
            else
            {
                Text = "WhatsApp Message Sender — मुदत: " + _lic.ExpiryDisplay;
            }
        }

        private void OnResource(object sender, CoreWebView2WebResourceRequestedEventArgs e)
        {
            string path = new Uri(e.Request.Uri).AbsolutePath;
            if (path == "/" || path == "/index.html")
            {
                e.Response = _env.CreateWebResourceResponse(new MemoryStream(_html), 200, "OK",
                    "Content-Type: text/html; charset=utf-8\r\nCache-Control: no-store");
            }
            else
            {
                e.Response = _env.CreateWebResourceResponse(new MemoryStream(new byte[0]), 404, "Not Found", "");
            }
        }

        private static void OpenExternal(string uri)
        {
            try
            {
                if (uri.StartsWith("https://", StringComparison.OrdinalIgnoreCase) ||
                    uri.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
                    uri.StartsWith("whatsapp:", StringComparison.OrdinalIgnoreCase))
                    Process.Start(new ProcessStartInfo(uri) { UseShellExecute = true });
            }
            catch { }
        }
    }
}
