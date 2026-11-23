package com.termux.app.features.system.floating;

import org.junit.Assert;
import org.junit.Test;

public class FloatingGeometryTest {

    @Test
    public void testClamp() {
        Assert.assertEquals(5, FloatingGeometry.clamp(5, 0, 10));
        Assert.assertEquals(0, FloatingGeometry.clamp(-5, 0, 10));
        Assert.assertEquals(10, FloatingGeometry.clamp(50, 0, 10));
        // The max wins if the range is empty.
        Assert.assertEquals(3, FloatingGeometry.clamp(8, 5, 3));
    }

    @Test
    public void testDefaultSize() {
        int[] size = FloatingGeometry.getDefaultSize(1000, 2000);
        Assert.assertEquals(900, size[0]);
        Assert.assertEquals(800, size[1]);
    }

    @Test
    public void testClampSize() {
        int[] size = FloatingGeometry.clampSize(50, 50, 1000, 2000, 300, 200);
        Assert.assertEquals(300, size[0]);
        Assert.assertEquals(200, size[1]);
        size = FloatingGeometry.clampSize(5000, 5000, 1000, 2000, 300, 200);
        Assert.assertEquals(1000, size[0]);
        Assert.assertEquals(2000, size[1]);
        // A screen smaller than the minimum size.
        size = FloatingGeometry.clampSize(100, 100, 250, 150, 300, 200);
        Assert.assertEquals(250, size[0]);
        Assert.assertEquals(150, size[1]);
    }

    @Test
    public void testClampPositionKeepsTitleBarReachable() {
        // 1000x2000 screen, 800 wide window, 100 px title bar, 150 px that must stay visible.
        int[] position = FloatingGeometry.clampPosition(-5000, -50, 800, 1000, 2000, 100, 150);
        Assert.assertEquals(-650, position[0]);
        Assert.assertEquals(0, position[1]);
        position = FloatingGeometry.clampPosition(5000, 5000, 800, 1000, 2000, 100, 150);
        Assert.assertEquals(850, position[0]);
        Assert.assertEquals(1900, position[1]);
        position = FloatingGeometry.clampPosition(100, 300, 800, 1000, 2000, 100, 150);
        Assert.assertEquals(100, position[0]);
        Assert.assertEquals(300, position[1]);
    }

    @Test
    public void testBubble() {
        int[] position = FloatingGeometry.clampBubble(-10, 5000, 100, 1000, 2000);
        Assert.assertEquals(0, position[0]);
        Assert.assertEquals(1900, position[1]);
        Assert.assertEquals(0, FloatingGeometry.snapBubbleToEdge(100, 100, 1000));
        Assert.assertEquals(900, FloatingGeometry.snapBubbleToEdge(600, 100, 1000));
    }

}
