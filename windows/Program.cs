using System;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace GccSender
{
    internal static class Program
    {
        [DllImport("user32.dll")] private static extern bool SetProcessDPIAware();

        [STAThread]
        private static void Main()
        {
            try { SetProcessDPIAware(); } catch { }
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            var lic = new LicenseManager();
            string msg = null;
            while (true)
            {
                int st = lic.LocalState();
                if (st != 0)
                {
                    if (st == 3) msg = "मुदत संपली आहे. नवीन Key साठी संपर्क करा.";
                    else if (st == 1) msg = "इंटरनेट चालू करून पुन्हा Activate/तपासणी करा.";
                    using (var f = new ActivationForm(lic, msg))
                        if (f.ShowDialog() != DialogResult.OK) return;
                }
                lic.TouchRun();
                var mf = new MainForm(lic);
                Application.Run(mf);
                if (!mf.RelockRequested) return;
                msg = mf.RelockMessage;
            }
        }
    }
}
