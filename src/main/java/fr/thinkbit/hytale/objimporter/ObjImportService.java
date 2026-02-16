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

    public static void importObj(World world, Path objPath, int x, int y, int z,
                                 int height, boolean solid,
                                 Consumer<String> logger) throws Exception {
        logger.accept("Parsing OBJ file...");
        ObjParser.ObjMesh mesh = ObjParser.parse(objPath);
        logger.accept("Mesh: " + mesh.vertices().size() + " vertices, " + mesh.faces().size() + " faces");

        logger.accept("Initializing block color index...");
        BlockColorIndex colorIndex = new BlockColorIndex();
        int defaultBlockId = colorIndex.findClosestBlock(128, 128, 128);
        BlockType defaultBt = BlockType.getAssetMap().getAsset(defaultBlockId);
        logger.accept("Default block: ID " + defaultBlockId +
                " -> " + (defaultBt != null ? defaultBt.getId() : "NULL"));

        Map<String, BufferedImage> textures = new HashMap<>();
        Map<String, Integer> materialBlockIds = new HashMap<>();
        loadMaterials(mesh, objPath, textures, materialBlockIds, colorIndex, logger);

        logger.accept("Voxelizing (height=" + height + ", solid=" + solid + ")...");
        MeshVoxelizer.VoxelResult result = MeshVoxelizer.voxelize(
                mesh, height, solid, textures, materialBlockIds, colorIndex, defaultBlockId);

        int solidCount = result.countSolid();
        logger.accept("Voxelized: " + result.sizeX() + "x" + result.sizeY() + "x" + result.sizeZ()
                + " (" + solidCount + " solid blocks)");

        placeBlocks(world, result, x, y, z, logger);
    }

    static void loadMaterials(ObjParser.ObjMesh mesh, Path objPath,
                              Map<String, BufferedImage> textures,
                              Map<String, Integer> materialBlockIds,
                              BlockColorIndex colorIndex,
                              Consumer<String> logger) {
        if (!mesh.hasMaterials() || mesh.mtlLib() == null) {
            logger.accept("No materials found");
            return;
        }

        try {
            Path mtlPath = objPath.getParent().resolve(mesh.mtlLib());
            logger.accept("Parsing MTL: " + mesh.mtlLib());
            Map<String, MtlParser.MtlMaterial> materials = MtlParser.parse(mtlPath);
            logger.accept("Found " + materials.size() + " materials");

            for (Map.Entry<String, MtlParser.MtlMaterial> entry : materials.entrySet()) {
                String name = entry.getKey();
                MtlParser.MtlMaterial mat = entry.getValue();

                if (mat.diffuseTexturePath() != null) {
                    try {
                        Path texPath = mtlPath.getParent().resolve(mat.diffuseTexturePath());
                        BufferedImage tex = TextureSampler.loadTexture(texPath);
                        textures.put(name, tex);

                        int[] avgColor = TextureSampler.getAverageColor(texPath);
                        int blockId = colorIndex.findClosestBlock(avgColor[0], avgColor[1], avgColor[2]);
                        materialBlockIds.put(name, blockId);
                        BlockType bt = BlockType.getAssetMap().getAsset(blockId);
                        logger.accept("  Texture " + name + " -> block " +
                                (bt != null ? bt.getId() : "ID:" + blockId));
                    } catch (Exception e) {
                        logger.accept("  Warning: texture failed for " + name + ": " + e.getMessage());
                    }
                }

                if (!materialBlockIds.containsKey(name)) {
                    int[] rgb = mat.getDiffuseColorRGB();
                    int blockId = colorIndex.findClosestBlock(rgb[0], rgb[1], rgb[2]);
                    materialBlockIds.put(name, blockId);
                    BlockType bt = BlockType.getAssetMap().getAsset(blockId);
                    logger.accept("  Color " + name + " (" + rgb[0] + "," + rgb[1] + "," + rgb[2] +
                            ") -> block " + (bt != null ? bt.getId() : "ID:" + blockId));
                }
            }
        } catch (Exception e) {
            logger.accept("Warning: MTL parsing failed: " + e.getMessage());
        }
    }

    static void placeBlocks(World world, MeshVoxelizer.VoxelResult result,
                            int originX, int originY, int originZ,
                            Consumer<String> logger) {
        boolean[][][] voxels = result.voxels();
        int[][][] blockIds = result.blockIds();

        // Pre-resolve block IDs to block name strings
        Map<Integer, String> blockNameCache = new HashMap<>();
        for (int bx = 0; bx < result.sizeX(); bx++) {
            for (int by = 0; by < result.sizeY(); by++) {
                for (int bz = 0; bz < result.sizeZ(); bz++) {
                    if (voxels[bx][by][bz]) {
                        int id = blockIds[bx][by][bz];
                        if (id > 0 && !blockNameCache.containsKey(id)) {
                            BlockType bt = BlockType.getAssetMap().getAsset(id);
                            if (bt != null) {
                                blockNameCache.put(id, bt.getId());
                            }
                        }
                    }
                }
            }
        }
        logger.accept("Resolved " + blockNameCache.size() + " unique block types");

        world.execute(() -> {
            int placed = 0;
            int skipped = 0;

            for (int bx = 0; bx < result.sizeX(); bx++) {
                for (int by = 0; by < result.sizeY(); by++) {
                    for (int bz = 0; bz < result.sizeZ(); bz++) {
                        if (voxels[bx][by][bz]) {
                            int blockId = blockIds[bx][by][bz];
                            String blockName = blockNameCache.get(blockId);
                            if (blockName != null) {
                                int wx = originX + bx;
                                int wy = originY + by;
                                int wz = originZ + bz;
                                world.setBlock(wx, wy, wz, blockName);
                                placed++;
                            } else {
                                skipped++;
                            }
                        }
                    }
                }
            }
            logger.accept("Import complete! Placed " + placed + " blocks" +
                    (skipped > 0 ? " (skipped " + skipped + " unknown)" : "") + ".");
        });
    }

    static int packRGB(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }
}
