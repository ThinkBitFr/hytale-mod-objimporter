package fr.thinkbit.hytale.objimporter;

import com.hypixel.hytale.builtin.buildertools.BlockColorIndex;
import com.hypixel.hytale.builtin.buildertools.objimport.MeshVoxelizer;
import com.hypixel.hytale.builtin.buildertools.objimport.MtlParser;
import com.hypixel.hytale.builtin.buildertools.objimport.ObjParser;
import com.hypixel.hytale.builtin.buildertools.objimport.TextureSampler;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ObjImportService {

    static final String DEFAULT_BLOCK = "hytale:stone";

    public static void importObj(World world, Path objPath, int x, int y, int z,
                                 int height, boolean solid,
                                 Consumer<String> logger) throws Exception {
        logger.accept("Parsing OBJ file...");
        ObjParser.ObjMesh mesh = ObjParser.parse(objPath);
        logger.accept("Mesh: " + mesh.vertices().size() + " vertices, " + mesh.faces().size() + " faces");

        MeshVoxelizer.VoxelResult result;

        if (mesh.hasMaterials() && mesh.mtlLib() != null) {
            result = voxelizeWithTextures(mesh, objPath, height, solid, logger);
        } else {
            logger.accept("No materials found, using simple voxelization...");
            result = MeshVoxelizer.voxelize(mesh, height, solid);
        }

        int solidCount = result.countSolid();
        logger.accept("Voxelized: " + result.sizeX() + "x" + result.sizeY() + "x" + result.sizeZ()
                + " (" + solidCount + " solid blocks)");

        placeBlocks(world, result, x, y, z, logger);
    }

    static MeshVoxelizer.VoxelResult voxelizeWithTextures(ObjParser.ObjMesh mesh, Path objPath,
                                                          int height, boolean solid,
                                                          Consumer<String> logger) throws Exception {
        Path mtlPath = objPath.getParent().resolve(mesh.mtlLib());
        logger.accept("Parsing MTL: " + mesh.mtlLib());
        Map<String, MtlParser.MtlMaterial> materials = MtlParser.parse(mtlPath);
        logger.accept("Found " + materials.size() + " materials");

        Map<String, BufferedImage> textures = new HashMap<>();
        Map<String, Integer> materialColors = new HashMap<>();

        for (Map.Entry<String, MtlParser.MtlMaterial> entry : materials.entrySet()) {
            String name = entry.getKey();
            MtlParser.MtlMaterial mat = entry.getValue();

            if (mat.diffuseTexturePath() != null) {
                try {
                    Path texPath = mtlPath.getParent().resolve(mat.diffuseTexturePath());
                    BufferedImage tex = TextureSampler.loadTexture(texPath);
                    textures.put(name, tex);

                    int[] avgColor = TextureSampler.getAverageColor(texPath);
                    materialColors.put(name, packRGB(avgColor[0], avgColor[1], avgColor[2]));
                    logger.accept("  Loaded texture for " + name);
                } catch (Exception e) {
                    logger.accept("  Warning: texture failed for " + name + ": " + e.getMessage());
                }
            }

            if (!materialColors.containsKey(name)) {
                int[] rgb = mat.getDiffuseColorRGB();
                materialColors.put(name, packRGB(rgb[0], rgb[1], rgb[2]));
                logger.accept("  Using diffuse color for " + name);
            }
        }

        logger.accept("Voxelizing with " + textures.size() + " textures (height=" + height + ", solid=" + solid + ")...");
        BlockColorIndex colorIndex = new BlockColorIndex();
        int defaultBlockId = BlockType.getAssetMap().getIndex(DEFAULT_BLOCK);

        return MeshVoxelizer.voxelize(mesh, height, solid, textures, materialColors, colorIndex, defaultBlockId);
    }

    static void placeBlocks(World world, MeshVoxelizer.VoxelResult result,
                            int originX, int originY, int originZ,
                            Consumer<String> logger) {
        // Pre-build block ID → block name cache
        Map<Integer, String> blockNameCache = new HashMap<>();
        boolean[][][] voxels = result.voxels();
        int[][][] blockIds = result.blockIds();
        for (int bx = 0; bx < result.sizeX(); bx++) {
            for (int by = 0; by < result.sizeY(); by++) {
                for (int bz = 0; bz < result.sizeZ(); bz++) {
                    if (voxels[bx][by][bz]) {
                        int id = blockIds[bx][by][bz];
                        if (id > 0 && !blockNameCache.containsKey(id)) {
                            BlockType bt = BlockType.getAssetMap().getAsset(id);
                            blockNameCache.put(id, bt != null ? bt.getId() : DEFAULT_BLOCK);
                        }
                    }
                }
            }
        }
        logger.accept("Using " + blockNameCache.size() + " distinct block types");

        world.execute(() -> {
            int placed = 0;
            for (int bx = 0; bx < result.sizeX(); bx++) {
                for (int by = 0; by < result.sizeY(); by++) {
                    for (int bz = 0; bz < result.sizeZ(); bz++) {
                        if (voxels[bx][by][bz]) {
                            int id = blockIds[bx][by][bz];
                            String blockName = blockNameCache.getOrDefault(id, DEFAULT_BLOCK);
                            world.setBlock(originX + bx, originY + by, originZ + bz, blockName);
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
