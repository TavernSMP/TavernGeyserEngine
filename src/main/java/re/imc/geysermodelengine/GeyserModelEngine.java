package re.imc.geysermodelengine;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.bone.type.Mount;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import re.imc.geysermodelengine.commands.ReloadCommand;
import re.imc.geysermodelengine.listener.ModelListener;
import re.imc.geysermodelengine.listener.MountPacketListener;
import re.imc.geysermodelengine.model.BedrockMountControl;
import re.imc.geysermodelengine.model.ModelEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class GeyserModelEngine extends JavaPlugin {

    @Getter
    private static GeyserModelEngine instance;

    @Getter
    private static boolean alwaysSendSkin;

    @Getter
    private int sendDelay;

    @Getter
    private int viewDistance;

    @Getter
    private Cache<Player, Boolean> joinedPlayer;

    @Getter
    private int joinSendDelay;

    @Getter
    private long entityPositionUpdatePeriod;

    @Getter
    private boolean debug;

    @Getter
    private Map<Player, Pair<ActiveModel, Mount>> drivers = new ConcurrentHashMap<>();

    @Getter
    private boolean initialized = false;

    @Getter
    private List<String> enablePartVisibilityModels = new ArrayList<>();

    @Getter
    private ScheduledExecutorService scheduler;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        PacketEvents.getAPI().init();
        saveDefaultConfig();
        sendDelay = getConfig().getInt("data-send-delay", 0);
        scheduler = Executors.newScheduledThreadPool(getConfig().getInt("thread-pool-size", 4));
        viewDistance = getConfig().getInt("entity-view-distance", 60);
        debug = getConfig().getBoolean("debug", false);
        joinSendDelay = getConfig().getInt("join-send-delay", 20);
        entityPositionUpdatePeriod = getConfig().getLong("entity-position-update-period", 35);
        enablePartVisibilityModels.addAll(getConfig().getStringList("enable-part-visibility-models"));
        if (joinSendDelay > 0) {
            joinedPlayer = CacheBuilder.newBuilder()
                    .expireAfterWrite(joinSendDelay * 50L, TimeUnit.MILLISECONDS).build();
        }
        instance = this;

        PacketEvents.getAPI().getEventManager().registerListener(new MountPacketListener(), PacketListenerPriority.NORMAL);

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                for (Map<ActiveModel, ModelEntity> models : ModelEntity.ENTITIES.values()) {
                    models.values().forEach(model -> model.getTask().updateEntityProperties(model.getViewers(), false));
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, 10, entityPositionUpdatePeriod, TimeUnit.MILLISECONDS);

        Bukkit.getPluginManager().registerEvents(new ModelListener(), this);

        getCommand("geysermodelengine").setExecutor(new ReloadCommand(this));
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
        for (Map<ActiveModel, ModelEntity> entities : ModelEntity.ENTITIES.values()) {
            entities.forEach((model, modelEntity) -> {
                modelEntity.getEntity().remove();
            });
        }
        // Plugin shutdown logic
    }

}