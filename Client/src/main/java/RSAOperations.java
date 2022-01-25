import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;

public class RSAOperations {

//	public static void main(String[] args) throws Exception {
//		
//		PrivateKey privKey = getPrivateKeyFromFile("Keys/server.key");
//		Certificate cert = getCertificate("Keys/server.crt");
//		PublicKey pubKey = cert.getPublicKey();
//		String msg = "Hello, I am someone";
//		String signature = sign(privKey, msg);
//		System.out.println(verify(pubKey, msg, signature));
//
//		Certificate cert2 = getCertificate("Keys/rootCA.crt");
//		PublicKey pubKey2 = cert2.getPublicKey();
//		String signature2 = sign(privKey, msg + "a");
//		System.out.println(verify(pubKey, msg, signature2));
//		System.out.println(verify(pubKey2, msg, signature));
//	}

	public static X509Certificate getCertificateFromPath(String certificatePath) throws Exception {
		CertificateFactory fac = CertificateFactory.getInstance("X509");
		FileInputStream is = new FileInputStream(certificatePath);
		X509Certificate cert = (X509Certificate) fac.generateCertificate(is);
		//TODO check validity of certificate?
		return cert;
	}

	public static X509Certificate getCertificateFromString(String strCertificate) throws Exception {
		CertificateFactory fac = CertificateFactory.getInstance("X509");
		X509Certificate cert = (X509Certificate) fac.generateCertificate(new ByteArrayInputStream(strCertificate.getBytes()));
		//TODO check validity of certificate?
		return cert;
	}

	public static String readFile(String filename) throws IOException {
		return new String(Files.readAllBytes(Paths.get(filename)), Charset.defaultCharset());
	}

	public static RSAPrivateKey getPrivateKeyFromFile(String filename) throws IOException, GeneralSecurityException {
		String privateKeyPEM = new String(Files.readAllBytes(Paths.get(filename)), Charset.defaultCharset());
		privateKeyPEM = privateKeyPEM.replace("-----BEGIN PRIVATE KEY-----\n", "");
		privateKeyPEM = privateKeyPEM.replace("-----END PRIVATE KEY-----", "");
		byte[] encoded = Base64.getMimeDecoder().decode((privateKeyPEM));
		KeyFactory kf = KeyFactory.getInstance("RSA");
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
		RSAPrivateKey privKey = (RSAPrivateKey) kf.generatePrivate(keySpec);
		return privKey;
	}

	public static String sign(PrivateKey privateKey, String message, String algorithm) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, UnsupportedEncodingException {
		Signature sign = Signature.getInstance(algorithm);
		sign.initSign(privateKey);
		sign.update(message.getBytes("UTF-8"));
		return new String(Base64.getEncoder().encodeToString((sign.sign())));
	}


	public static boolean verify(PublicKey publicKey, String message, String signature, String algorithm) throws SignatureException, NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeyException {
		Signature sign = Signature.getInstance(algorithm);
		sign.initVerify(publicKey);
		sign.update(message.getBytes("UTF-8"));
		return sign.verify(Base64.getMimeDecoder().decode((signature.getBytes("UTF-8"))));
	}

//	public static String encrypt(String rawText, PublicKey publicKey) throws IOException, GeneralSecurityException {
//	    Cipher cipher = Cipher.getInstance("RSA");
//	    cipher.init(Cipher.ENCRYPT_MODE, publicKey);
//	    return Base64.getMimeEncoder().encodeToString((cipher.doFinal(rawText.getBytes("UTF-8"))));
//	}
//
//	public static String decrypt(String cipherText, PrivateKey privateKey) throws IOException, GeneralSecurityException {
//	    Cipher cipher = Cipher.getInstance("RSA");
//	    cipher.init(Cipher.DECRYPT_MODE, privateKey);
//	    return new String(cipher.doFinal(Base64.getMimeDecoder().decode((cipherText))), "UTF-8");
//	}


}
