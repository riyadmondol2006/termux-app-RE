package com.termux.app.features.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TerminalFeaturesControllerTest {

    @Test
    public void testGetSearchText() {
        assertEquals("error", TerminalFeaturesController.getSearchText("  error \n"));
        assertEquals("first line", TerminalFeaturesController.getSearchText("first line\nsecond line"));
        assertNull(TerminalFeaturesController.getSearchText(" \n "));
        assertNull(TerminalFeaturesController.getSearchText(null));
    }

}
