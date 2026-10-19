package com.safenavi.app.safety;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import com.safenavi.app.product.ServerConnection;

public class ServerConnectionTest {
    @Test public void cloudUpdateMigratesOldLaptopOriginOnce(){
        org.junit.Assert.assertTrue(ServerConnection.shouldMigrateToCloud("http://192.168.1.2:8000",ServerConnection.CLOUD_ORIGIN,false));
        org.junit.Assert.assertFalse(ServerConnection.shouldMigrateToCloud(ServerConnection.CLOUD_ORIGIN,ServerConnection.CLOUD_ORIGIN,false));
        org.junit.Assert.assertFalse(ServerConnection.shouldMigrateToCloud("http://192.168.1.2:8000",ServerConnection.CLOUD_ORIGIN,true));
        org.junit.Assert.assertFalse(ServerConnection.shouldMigrateToCloud("http://192.168.1.2:8000","http://192.168.1.2:8000",false));
    }
    @Test public void acceptsPrivateHotspotAndHealthLink() {
        assertEquals("http://172.24.168.186:8000",ServerConnection.normalize("172.24.168.186:8000"));
        assertEquals("http://192.168.29.221:8000",ServerConnection.normalize("http://192.168.29.221:8000/health"));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsPublicHttp(){ServerConnection.normalize("http://example.com:8000");}
    @Test(expected=IllegalArgumentException.class) public void rejectsCredentials(){ServerConnection.normalize("https://user:password@example.com");}
    @Test(expected=IllegalArgumentException.class) public void rejectsPrivateLookingDns(){ServerConnection.normalize("http://10.1.evil.example:8000");}
    @Test(expected=IllegalArgumentException.class) public void rejectsApiPath(){ServerConnection.normalize("http://172.24.168.186:8000/api/v1");}
    @Test(expected=IllegalArgumentException.class) public void rejectsLoopbackLookingDns(){ServerConnection.normalize("http://127.evil.example:8000");}
    @Test(expected=IllegalArgumentException.class) public void rejectsInvalidPort(){ServerConnection.normalize("https://example.com:99999");}
    @Test(expected=IllegalArgumentException.class) public void rejectsNonHttpScheme(){ServerConnection.normalize("ftp://example.com");}
    @Test public void normalizesHttpsAndDefaultPort(){assertEquals("https://example.com",ServerConnection.normalize("HTTPS://EXAMPLE.COM:443/health"));}
    @Test public void cloudSwitchRequiresQueueDrainButHotspotIpChangeStillWorks(){
        org.junit.Assert.assertTrue(ServerConnection.requiresCleanQueue("http://192.168.1.2:8000", "https://example.onrender.com"));
        org.junit.Assert.assertTrue(ServerConnection.requiresCleanQueue("https://old.onrender.com", "https://new.onrender.com"));
        org.junit.Assert.assertFalse(ServerConnection.requiresCleanQueue("http://192.168.1.2:8000", "http://192.168.1.3:8000"));
        org.junit.Assert.assertFalse(ServerConnection.requiresCleanQueue("https://example.onrender.com", "https://example.onrender.com"));
    }
}
