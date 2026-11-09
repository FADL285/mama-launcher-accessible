package info.fadl.mamalauncher;

import android.speech.tts.TextToSpeech;
import org.junit.Test;
import java.io.File;
import java.io.InputStream;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

/** Checks the actual merged manifest. Robolectric does not enforce Android
 * 11 package visibility, so activity tests alone cannot catch this contract. */
public class TtsManifestTest {
    @Test public void mergedManifestDeclaresTheRealTtsServiceAction() throws Exception {
        Properties config = new Properties();
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "com/android/tools/test_config.properties")) {
            assertNotNull("AGP merged-manifest test config", stream);
            config.load(stream);
        }
        String path = config.getProperty("android_merged_manifest");
        assertNotNull("merged manifest path", path);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Element root = factory.newDocumentBuilder().parse(new File(path)).getDocumentElement();
        NodeList queries = root.getElementsByTagName("queries");
        boolean found = false;
        for (int i = 0; i < queries.getLength(); i++) {
            NodeList actions = ((Element) queries.item(i)).getElementsByTagName("action");
            for (int j = 0; j < actions.getLength(); j++) {
                String action = ((Element) actions.item(j)).getAttributeNS(
                        "http://schemas.android.com/apk/res/android", "name");
                assertFalse("Java constant identifier is not an Android action value",
                        action.contains("INTENT_ACTION_TTS_SERVICE"));
                if (TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE.equals(action)) found = true;
            }
        }
        assertTrue("Android 11+ must be able to discover TTS engines", found);
    }
}
