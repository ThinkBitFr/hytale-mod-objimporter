package fr.thinkbit.hytale.objimporter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObjImportServiceTest {

    @Test
    void packRGB_allZeros() {
        assertEquals(0x000000, ObjImportService.packRGB(0, 0, 0));
    }

    @Test
    void packRGB_white() {
        assertEquals(0xFFFFFF, ObjImportService.packRGB(255, 255, 255));
    }

    @Test
    void packRGB_red() {
        assertEquals(0xFF0000, ObjImportService.packRGB(255, 0, 0));
    }

    @Test
    void packRGB_green() {
        assertEquals(0x00FF00, ObjImportService.packRGB(0, 255, 0));
    }

    @Test
    void packRGB_blue() {
        assertEquals(0x0000FF, ObjImportService.packRGB(0, 0, 255));
    }

    @Test
    void packRGB_arbitraryColor() {
        assertEquals(0x804020, ObjImportService.packRGB(128, 64, 32));
    }

    @Test
    void packRGB_singleChannelMax() {
        assertEquals(0xFF0000, ObjImportService.packRGB(255, 0, 0));
        assertEquals(0x00FF00, ObjImportService.packRGB(0, 255, 0));
        assertEquals(0x0000FF, ObjImportService.packRGB(0, 0, 255));
    }

    @Test
    void packRGB_midValues() {
        assertEquals(0x808080, ObjImportService.packRGB(128, 128, 128));
    }

    @Test
    void packRGB_components() {
        int packed = ObjImportService.packRGB(0xAB, 0xCD, 0xEF);
        assertEquals(0xAB, (packed >> 16) & 0xFF);
        assertEquals(0xCD, (packed >> 8) & 0xFF);
        assertEquals(0xEF, packed & 0xFF);
    }
}
