import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** Assina o APK (esquemas v1, v2 e v3) e confere a assinatura. Uso: Assina chave.p12 senha entrada.apk saida.apk */
public class Assina {
    public static void main(String[] a) throws Exception {
        char[] senha = a[1].toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(a[0])) { ks.load(in, senha); }
        String alias = ks.aliases().nextElement();
        PrivateKey chave = (PrivateKey) ks.getKey(alias, senha);
        X509Certificate cert = (X509Certificate) ks.getCertificate(alias);

        ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder(
                "caixa", chave, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(cfg))
                .setInputApk(new File(a[2]))
                .setOutputApk(new File(a[3]))
                .setMinSdkVersion(24)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
                .sign();

        ApkVerifier.Result r = new ApkVerifier.Builder(new File(a[3])).build().verify();
        if (!r.isVerified()) {
            System.err.println("Assinatura inválida: " + r.getErrors());
            System.exit(1);
        }
        System.out.println("Assinado e verificado (v1=" + r.isVerifiedUsingV1Scheme()
                + ", v2=" + r.isVerifiedUsingV2Scheme() + ", v3=" + r.isVerifiedUsingV3Scheme() + ")");
    }
}
