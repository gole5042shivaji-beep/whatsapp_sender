#!/usr/bin/env python3
"""index.html -> index.html.enc  (Android आणि Windows दोन्हीसाठी)
वापर:  python encrypt_html.py index_app.html   (pip install cryptography)
आउटपुट: android/app/src/main/assets/index.html.enc  आणि  windows/index.html.enc"""
import hmac,hashlib,os,sys
from cryptography.hazmat.primitives.ciphers import Cipher,algorithms,modes
from cryptography.hazmat.primitives import padding
A=bytes.fromhex("b997b87b56d0314ff3466d80d2d56496702e6540dd9e8e8a6c05b6dcc60712fa")
B=bytes.fromhex("99497f9defdfeef89788a3ac14cf9f9b489a2771c172d4b28a0c9cea156870a7")
master=bytes(x^y for x,y in zip(A,B))
ek=hmac.new(master,b"gcctbc-enc",hashlib.sha256).digest();mk=hmac.new(master,b"gcctbc-mac",hashlib.sha256).digest()
def enc(plain):
    iv=os.urandom(16);pd=padding.PKCS7(128).padder();d=pd.update(plain)+pd.finalize()
    c=Cipher(algorithms.AES(ek),modes.CBC(iv)).encryptor();ct=c.update(d)+c.finalize()
    body=b"GTE1"+iv+ct
    return body+hmac.new(mk,body,hashlib.sha256).digest()
data=enc(open(sys.argv[1],"rb").read())
root=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for out in ("android/app/src/main/assets/index.html.enc","windows/index.html.enc"):
    open(os.path.join(root,out),"wb").write(data)
print("ok",len(data))
