import org.junit.*;
import java.util.*;
import play.test.*;
import util.Util;
import models.*;

public class BasicTest extends UnitTest {

    @Test
    public void versionComparisonTests() {
        assertEquals(0, Util.compareVersions("", ""));
        
        assertEquals(-1, Util.compareVersions("", "a"));
        assertEquals(1, Util.compareVersions("a", ""));
        assertEquals(0, Util.compareVersions("a", "a"));
        
        assertEquals(-1, Util.compareVersions("a", "b"));
        assertEquals(1, Util.compareVersions("b", "a"));
        assertEquals(-1, Util.compareVersions("a", "-"));
        assertEquals(1, Util.compareVersions("-", "a"));

        assertEquals(-1, Util.compareVersions("a", "aa"));
        assertEquals(1, Util.compareVersions("aa", "a"));

        assertEquals(0, Util.compareVersions("a1", "a1"));
        assertEquals(-1, Util.compareVersions("a1", "a2"));
        assertEquals(0, Util.compareVersions("a001", "a1"));

        assertEquals(0, Util.compareVersions("1.0.2", "1.0.2"));
        assertEquals(-1, Util.compareVersions("1.0.2", "1.0.2.4"));
        assertEquals(-1, Util.compareVersions("1.0.2", "1.0.2b"));
        assertEquals(-1, Util.compareVersions("1.0.2", "1.0.2RC"));
        assertEquals(-1, Util.compareVersions("1.0.2", "1.0.2-RC"));

        assertEquals(-1, Util.compareVersions("0.3", "2.2.4"));

        assertEquals(-1, Util.compareVersions("1.0.2", "1.2"));
        assertEquals(-1, Util.compareVersions("1.0.2", "2"));
        assertEquals(-1, Util.compareVersions("1.0.2", "2.2.4"));
        
        assertEquals(-1, Util.compareVersions("1.0", "1.0.2"));
    }

}
