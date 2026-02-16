package fr.thinkbit.hytale.objimporter;

import com.hypixel.hytale.builtin.buildertools.objimport.MeshVoxelizer;
import com.hypixel.hytale.builtin.buildertools.objimport.ObjParser;
import com.hypixel.hytale.server.core.universe.world.World;

import java.nio.file.Path;
import java.util.function.Consumer;

public class ObjImportService {

    static final String DEFAULT_BLOCK = "hytale:stone";

    public static void importObj(World world, Path objPath, int x, int y, int z,
                                 int height, boolean solid,
                                 Consumer<String> logger) throws Exception {
        logger.accept("Parsing OBJ file...");
        ObjParser.ObjMesh mesh = ObjParser.parse(objPath);
        logger.accept("Mesh: " + mesh.vertices().size() + " vertices, " + mesh.faces().size() + " faces");

        logger.accept("Voxelizing (height=" + height + ", solid=" + solid + ")...");
        MeshVoxelizer.VoxelResult result = MeshVoxelizer.voxelize(mesh, height, solid);

        int solidCount = result.countSolid();
        logger.accept("Voxelized: " + result.sizeX() + "x" + result.sizeY() + "x" + result.sizeZ()
                + " (" + solidCount + " solid blocks)");

        placeBlocks(world, result, x, y, z, logger);
    }

    static void placeBlocks(World world, MeshVoxelizer.VoxelResult result,
                            int originX, int originY, int originZ,
                            Consumer<String> logger) {
        boolean[][][] voxels = result.voxels();
        world.execute(() -> {
            int placed = 0;
            for (int bx = 0; bx < result.sizeX(); bx++) {
                for (int by = 0; by < result.sizeY(); by++) {
                    for (int bz = 0; bz < result.sizeZ(); bz++) {
                        if (voxels[bx][by][bz]) {
                            world.setBlock(originX + bx, originY + by, originZ + bz, DEFAULT_BLOCK);
                            placed++;
                        }
                    }
                }
            }
            logger.accept("Import complete! Placed " + placed + " blocks.");
        });
    }

    static int packRGB(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }
}
