import static util.ModuleChecker.MODULE_NAME_PATTERN;
import static util.ModuleChecker.MODULE_VERSION_PATTERN;

import org.junit.Test;

import play.test.UnitTest;
import util.Util;

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

        assertEquals(-1, Util.compareVersions("0.3", "0.3.1"));
        assertEquals(1, Util.compareVersions("0.3.1", "0.3"));
        assertEquals(-1, Util.compareVersions("0.3.1", "0.3.2"));
        assertEquals(1, Util.compareVersions("0.3.2", "0.3.1"));
    }
    
    @Test
    public void shouldAcceptModuleName() {
        String[] shouldAcceptModuleNames = new String[] {
                "a",
                "a.a",
                "a.a.a",
                "a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a",
                "a.b.c.x.y.z",
                "a.bb.ccc.xxxx.yyyyy.zzzzzz",
                "aaaaa.bbbbb.ccccc.xxxxx.yyyyy.zzzzz" };

        for (String moduleName : shouldAcceptModuleNames) {
            assertTrue("Should accept module name : " + moduleName, MODULE_NAME_PATTERN.matcher(moduleName).matches());
        }
    }
    
    @Test
    public void shouldRejectModuleName() {
        String[] shouldRejectModuleNames = new String[] {
                "",
                " ",
                ".",
                "..",
                ".a",
                "a.",
                ".a.",
                "a.b.c.",
                ".a.b.c",
                ".a.b.c.",
                "A",
                "aA",
                "a.A",
                "#",
                "a#",
                "a.#",
                "1",
                "a1",
                "a.1",
                "a ",
                " a"};

        for (String moduleName : shouldRejectModuleNames) {
            assertFalse("Should reject module name : " + moduleName, MODULE_NAME_PATTERN.matcher(moduleName).matches());
        }
    }
    
    @Test
    public void shouldAcceptModuleVersion() {
        String[] shouldAcceptModuleVersions = new String[] {
                "1",
                "1.2",
                "1.2.3",
                "1234567890",
                "1a",
                "1a.2b",
                "alfa" };

        for (String moduleVersion : shouldAcceptModuleVersions) {
            assertTrue("Should accept module version : " + moduleVersion, MODULE_VERSION_PATTERN.matcher(moduleVersion).matches());
        }
    }
    
    @Test
    public void shouldRejectModuleVersion() {
        String[] shouldRejectModuleVersions = new String[] {
                "",
                " ",
                ".",
                "..",
                ".1",
                "1.",
                ".1.",
                "1.2.3.",
                ".1.2.3",
                ".1.2.3.",
                "@",
                "1@",
                "1.@",
                "1 ",
                " 1"};

        for (String moduleVersion : shouldRejectModuleVersions) {
            assertFalse("Should reject module version : " + moduleVersion, MODULE_NAME_PATTERN.matcher(moduleVersion).matches());
        }
    }    

}