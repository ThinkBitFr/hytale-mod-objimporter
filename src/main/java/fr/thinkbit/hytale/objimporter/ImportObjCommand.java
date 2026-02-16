package fr.thinkbit.hytale.objimporter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.nio.file.Path;

public class ImportObjCommand extends AbstractPlayerCommand {

    private final Path modDataDir;

    private final RequiredArg<String> fileArg;
    private final RequiredArg<Integer> xArg;
    private final RequiredArg<Integer> yArg;
    private final RequiredArg<Integer> zArg;
    private final DefaultArg<Integer> heightArg;
    private final DefaultArg<Boolean> solidArg;

    public ImportObjCommand(ObjImporterPlugin plugin) {
        super("importobj", "Import an OBJ 3D model as voxelized blocks");
        this.modDataDir = Path.of("mods", "ObjImporter");

        this.fileArg = withRequiredArg("file", "Path to OBJ file relative to models dir", ArgTypes.STRING);
        this.xArg = withRequiredArg("x", "X origin coordinate", ArgTypes.INTEGER);
        this.yArg = withRequiredArg("y", "Y origin coordinate", ArgTypes.INTEGER);
        this.zArg = withRequiredArg("z", "Z origin coordinate", ArgTypes.INTEGER);
        this.heightArg = withDefaultArg("height", "Model height in blocks", ArgTypes.INTEGER, 100, "100");
        this.solidArg = withDefaultArg("solid", "Fill interior", ArgTypes.BOOLEAN, true, "true");
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store,
                           Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        String file = context.get(fileArg);
        int x = context.get(xArg);
        int y = context.get(yArg);
        int z = context.get(zArg);
        int height = context.get(heightArg);
        boolean solid = context.get(solidArg);

        Path objPath = modDataDir.resolve("models").resolve(file);

        if (!objPath.toFile().exists()) {
            context.sendMessage(Message.raw("Error: OBJ file not found: " + objPath));
            return;
        }

        context.sendMessage(Message.raw("Starting OBJ import: " + file));
        context.sendMessage(Message.raw("Position: (" + x + ", " + y + ", " + z + ") height=" + height + " solid=" + solid));

        try {
            ObjImportService.importObj(
                    world, objPath, x, y, z, height, solid,
                    msg -> context.sendMessage(Message.raw(msg))
            );
        } catch (Exception e) {
            context.sendMessage(Message.raw("Error during import: " + e.getMessage()));
        }
    }
}
