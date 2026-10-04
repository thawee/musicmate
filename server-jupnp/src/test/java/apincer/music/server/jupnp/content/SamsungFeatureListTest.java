package apincer.music.server.jupnp.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jupnp.binding.annotations.AnnotationLocalServiceBinder;
import org.jupnp.model.meta.Action;
import org.jupnp.model.meta.LocalService;
import org.junit.Test;

public class SamsungFeatureListTest {

    @Test
    public void contentDirectory_exposesXGetFeatureList() {
        LocalService<?> service = new AnnotationLocalServiceBinder().read(ContentDirectory.class);
        Action<?> action = service.getAction("X_GetFeatureList");
        assertNotNull("Samsung TVs call X_GetFeatureList on ContentDirectory", action);
        assertEquals("FeatureList", action.getOutputArguments()[0].getName());
        assertEquals("A_ARG_TYPE_FeatureList", action.getOutputArguments()[0].getRelatedStateVariableName());
    }

    @Test
    public void featureList_pointsSamsungBasicViewAtTheMusicRoot() {
        String xml = ContentDirectory.SAMSUNG_FEATURE_LIST;
        assertTrue(xml.contains("<Features xmlns=\"urn:schemas-upnp-org:av:avs\""));
        assertTrue(xml.contains("<Feature name=\"samsung.com_BASICVIEW\" version=\"1\">"));
        assertTrue(xml.contains("<container id=\"0\" type=\"object.item.audioItem\"/>"));
        // audio only: no video or photo entries the server could not fill
        assertFalse(xml.contains("videoItem"));
        assertFalse(xml.contains("imageItem"));
    }
}
