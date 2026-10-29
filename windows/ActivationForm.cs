using System;
using System.Drawing;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace GccSender
{
    internal class ActivationForm : Form
    {
        private readonly LicenseManager _lic;
        private readonly TextBox _key = new TextBox();
        private readonly Button _go = new Button();
        private readonly Label _status = new Label();

        public ActivationForm(LicenseManager lic, string message)
        {
            _lic = lic;
            Text = "WhatsApp Message Sender — Activation";
            try { Icon = System.Drawing.Icon.ExtractAssociatedIcon(System.Reflection.Assembly.GetExecutingAssembly().Location); } catch { }
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false; MinimizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(520, 330);
            Font = new Font("Segoe UI", 10f);
            BackColor = Color.FromArgb(0xF2, 0xF4, 0xFA);

            var title = new Label { Text = "🔑 WhatsApp Message Sender", Font = new Font("Segoe UI", 15f, FontStyle.Bold),
                                    Left = 24, Top = 20, Width = 470, Height = 34 };
            var sub = new Label { Text = "Activation साठी आपली License Key टाका", Left = 24, Top = 58, Width = 470, ForeColor = Color.DimGray };
            _key.Left = 24; _key.Top = 92; _key.Width = 470; _key.Font = new Font("Consolas", 14f);
            _key.CharacterCasing = CharacterCasing.Upper;
            _key.Text = lic.SavedKey;
            _go.Text = "Activate करा"; _go.Left = 24; _go.Top = 140; _go.Width = 470; _go.Height = 40;
            _go.BackColor = Color.FromArgb(0x3F, 0x51, 0xB5); _go.ForeColor = Color.White; _go.FlatStyle = FlatStyle.Flat;
            _go.Font = new Font("Segoe UI", 11f, FontStyle.Bold);
            _go.Click += async (s, e) => await DoActivate();
            AcceptButton = _go;
            _status.Left = 24; _status.Top = 190; _status.Width = 470; _status.Height = 50;
            _status.ForeColor = Color.Firebrick; _status.Text = message ?? "";

            var idLbl = new Label { Text = "या PC चा Machine ID:", Left = 24, Top = 250, Width = 470, ForeColor = Color.DimGray };
            var id = new TextBox { Text = lic.MachineId, Left = 24, Top = 274, Width = 370, ReadOnly = true, Font = new Font("Consolas", 10f) };
            var copy = new Button { Text = "Copy", Left = 402, Top = 272, Width = 92, Height = 28 };
            copy.Click += (s, e) => { try { Clipboard.SetText(lic.MachineId); } catch { } };

            Controls.AddRange(new Control[] { title, sub, _key, _go, _status, idLbl, id, copy });
        }

        private async Task DoActivate()
        {
            string k = _key.Text.Trim().ToUpperInvariant();
            if (k.Length < 8) { _status.ForeColor = Color.Firebrick; _status.Text = "Key टाका."; return; }
            _go.Enabled = false;
            _status.ForeColor = Color.DimGray; _status.Text = "तपासत आहे…";
            LicenseResult r = await Task.Run(() => _lic.Activate(k));
            _go.Enabled = true;
            if (r.Ok)
            {
                DialogResult = DialogResult.OK;
                Close();
            }
            else
            {
                _status.ForeColor = Color.Firebrick;
                _status.Text = r.Message;
            }
        }
    }
}
