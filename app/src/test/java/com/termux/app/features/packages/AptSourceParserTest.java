package com.termux.app.features.packages;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AptSourceParserTest {

    @Test
    public void testParseEnabledAndDisabledSources() {
        AptSourceParser.ParsedSource enabled = AptSourceParser.parse(
            "deb [arch=aarch64 signed-by=/key.gpg] https://example.com/apt stable main");
        Assert.assertNotNull(enabled);
        Assert.assertTrue(enabled.enabled);
        Assert.assertTrue(enabled.declaration.contains(" https://example.com/apt "));

        AptSourceParser.ParsedSource disabled = AptSourceParser.parse("  # deb https://example.com/apt stable main  ");
        Assert.assertNotNull(disabled);
        Assert.assertFalse(disabled.enabled);
        Assert.assertEquals("deb https://example.com/apt stable main", disabled.declaration);
    }

    @Test
    public void testRejectsCommentsAndInvalidDeclarations() {
        Assert.assertNull(AptSourceParser.parse("# ordinary comment"));
        Assert.assertNull(AptSourceParser.parse(""));
        Assert.assertNull(AptSourceParser.parse("deb https://example.com"));
        Assert.assertNull(AptSourceParser.parse("Types: deb"));
    }

    @Test
    public void testSetEnabledAndRemoveSource() {
        List<String> lines = new ArrayList<>(Arrays.asList("# comment", "deb https://example.com stable main"));
        AptSourceParser.setEnabled(lines, 1, false);
        Assert.assertEquals("# deb https://example.com stable main", lines.get(1));
        AptSourceParser.setEnabled(lines, 1, true);
        Assert.assertEquals("deb https://example.com stable main", lines.get(1));
        AptSourceParser.setEnabled(lines, 0, true);
        Assert.assertEquals("# comment", lines.get(0));
    }

}
