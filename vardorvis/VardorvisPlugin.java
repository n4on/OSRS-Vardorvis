package net.runelite.client.plugins.microbot.vardorvis;


import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.vardorvis.enums.State;
import net.runelite.client.plugins.microbot.vardorvis.enums.StateBank;
import net.runelite.client.plugins.microbot.vardorvis.enums.StatePOH;
import net.runelite.client.ui.overlay.OverlayManager;
import javax.inject.Inject;
import java.awt.*;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.npc.Rs2Npc.interact;
import static net.runelite.client.plugins.microbot.vardorvis.VardorvisScript.*;

@PluginDescriptor(
        name = "<html>[<font color=red>Neon</font>] " + "Vardorvis",
        description = "Vardorvis killer",
        tags = {"vardorvis", "boss"},
        enabledByDefault = false
)
@Slf4j
public class VardorvisPlugin extends Plugin {
    private ScheduledExecutorService scheduler;
    private ExecutorService executor;
    private ExecutorService sleepUntilExecutor;
    private ExecutorService walkingExecutor;

    private static final int RANGE_PROJECTILE = 1343;

    private final Set<Integer> trackedWidgets = Set.of(54591499, 54591498, 54591497, 54591496, 54591495, 54591494);

    private int tickCounter = 0;

    public static boolean oppositeAxe = false;
    public static int oppositeAxeCounter = 0;

    public static int currentRunningTicks = 0;

    public static Rs2PrayerEnum currentPrayer = null;

    public static boolean axeOnSafeTile = false;
    private static int axeOnSafeTileTick = 0;

    @Inject
    private VardorvisConfig config;
    @Provides
    VardorvisConfig provideConfig(ConfigManager configManager) { return configManager.getConfig(VardorvisConfig.class); }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private VardorvisOverlay vardorvisOverlay;
    @Inject
    VardorvisScript vardorvisScript;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(vardorvisOverlay);
        }
        vardorvisScript.run(config);

        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            executor = Executors.newSingleThreadExecutor();
        }
        if (scheduler == null || scheduler.isShutdown() || scheduler.isTerminated()) {
            scheduler = Executors.newScheduledThreadPool(1);
        }
        if (sleepUntilExecutor == null || sleepUntilExecutor.isShutdown() || sleepUntilExecutor.isTerminated()) {
            sleepUntilExecutor = Executors.newSingleThreadExecutor();
        }
        if (walkingExecutor == null || walkingExecutor.isShutdown() || walkingExecutor.isTerminated()) {
            walkingExecutor = Executors.newSingleThreadExecutor();
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        currentRunningTicks++;
        if (Microbot.getClient().isPrayerActive(Prayer.PROTECT_FROM_MELEE) && currentPrayer != Rs2PrayerEnum.PROTECT_MELEE) {
            currentPrayer = Rs2PrayerEnum.PROTECT_MELEE;
            //Microbot.log("Prayer swapped to MELEE");

        } else if (Microbot.getClient().isPrayerActive(Prayer.PROTECT_FROM_MISSILES) && currentPrayer != Rs2PrayerEnum.PROTECT_RANGE) {
            currentPrayer = Rs2PrayerEnum.PROTECT_RANGE;

            //Microbot.log("Prayer swapped to RANGE");
        }

        if (Rs2Inventory.getInventoryFood().isEmpty() && Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) < 30) {
            Microbot.log("You were going to dieee!!");
            Rs2Inventory.interact("Teleport to house", "break");

            state = State.POH;

            sleep(4000);

            Microbot.log("Turning state to POH");

            state = State.POH;
        }

        if (Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) < 20) {
            //Microbot.log("Using food because < 20 health");
            handleHealingAndPrayer();
        }

        if (Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) < 10) {
            handleHealingAndPrayer();
        }

        if (axeOnSafeTile) {
            axeOnSafeTileTick++;

            if (axeOnSafeTileTick >= 3) { // 4
                axeOnSafeTileTick = 0;
                axeOnSafeTile = false;
            }
        }

        if (oppositeAxe) {
            oppositeAxeCounter++;
        }
        if (oppositeAxeCounter == 9) {
            oppositeAxe = false;
        }

        if (currentPrayer != Rs2PrayerEnum.PROTECT_MELEE && !VardorvisScript.isProjectileActive && VardorvisScript.inFight && VardorvisScript.inInstance) {
            Microbot.log("Toggling Protect Melee ON | Current tick = " + currentRunningTicks);
            walkingExecutor.submit(() -> {
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
            });
        }

        if(Objects.equals(Rs2Player.getWorldLocation(), new WorldPoint(1131, 3421, 0))) {

        } else if (Objects.equals(Rs2Player.getWorldLocation(), new WorldPoint(1129, 3423, 0))) {
            int poseAnimation = Rs2Player.getPoseAnimation();
            if (poseAnimation == 1205 || poseAnimation == 1210) { // poseAnimation == 1205 || poseAnimation == 1206 || poseAnimation == 1208 ||
                Microbot.log("Healing | pose animation = " + poseAnimation + " | Current tick = " + currentRunningTicks);

                handleHealingAndPrayer();

                sleepUntilExecutor.submit(() -> {
                    boolean conditionsMet = sleepUntil(() -> {
                        boolean walkingCheck = !Rs2Player.isWalking();
                        boolean animatingCheck = !Rs2Player.isAnimating(200);
                        boolean projectileCheck = !isProjectileActive;
                        boolean oppositeAxeBoolean = !oppositeAxe;

                        return walkingCheck && animatingCheck && projectileCheck && oppositeAxeBoolean;
                    }, 2400);

                    Microbot.getClientThread().invokeLater(() -> {
                        if (conditionsMet) {
                            if (Rs2Combat.getSpecEnergy() >= 500) {
                                Microbot.log("Speccing vardorvis after heal | Current tick = " + currentRunningTicks);

                                specVardorvis();
                            } else {
                                Microbot.log("Hitting vardorvis after heal | Current tick = " + currentRunningTicks);
                                Rs2Npc.interact(12223, "Attack");
                            }
                        }
                    });
                });
            }
        } else if (VardorvisScript.inFight && Rs2Npc.getNpc("Vardorvis").getHealthRatio() != -1) {
            Microbot.log("Somehow on a wrong tile? moving back to safe tile | Current tick = " + currentRunningTicks + " Health ratio = " + Rs2Npc.getNpc("Vardorvis").getHealthRatio());
            walkingExecutor.submit(() -> {
                Rs2Walker.walkFastCanvas(new WorldPoint(1129, 3423, 0));
            }, 50);

            handleHealingAndPrayer();
        }
    }

    @Subscribe
    public void onClientTick(ClientTick tick) {

        if (isBloodSplatsVisible()) {
            clickingBloodSplats();
        }

        if (Objects.equals(Rs2Player.getWorldLocation(), new WorldPoint(1131, 3421, 0))) {
            tickCounter++;
        } else if (Objects.equals(Rs2Player.getWorldLocation(), new WorldPoint(1129, 3423, 0))) {
            tickCounter = 0;
        }

        if (tickCounter >= 30) {
            if (!axeOnSafeTile) {
                Microbot.log("Safe to move back to default tile | Tick counter = " + tickCounter + "  | Current tick = " + currentRunningTicks);
                walkingExecutor.submit(() -> {
                    Rs2Walker.walkFastCanvas(new WorldPoint(1129,3423,0));
                }, 50);
                tickCounter = 0;
            }
        }

        checkAxeLocations();
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        NPC npc = event.getNpc();

        if (npc.getId() == 12225) {

            if (npc.getLocalLocation().getX() == 8384 && npc.getLocalLocation().getY() == 6080) {

                Microbot.log("Axe spawned on you moving to avoid tile | Current ticks = " + currentRunningTicks);
                walkingExecutor.submit(() -> {
                    Rs2Walker.walkFastCanvas(new WorldPoint(1131,3421,0));
                }, 50);
                axeOnSafeTile = true;
            }

            if (npc.getLocalLocation().getX() == 8384 && npc.getLocalLocation().getY() == 4800) {
                //Microbot.log("Axe spawned opposite of you | Current ticks = " + currentRunningTicks);
                oppositeAxe = true;
            }
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage message) {
        if (message.getMessage().contains("manage to escape")) {
            //Microbot.log("You escaped the blood splats, now healing!");

            handleHealingAndPrayer();

            if (!VardorvisScript.isProjectileActive && Rs2Player.isAnimating()) {
                Microbot.log("Hitting Vardorvis, after blood splats  | Current tick = " + currentRunningTicks);
                if (Rs2Player.isWalking()) {
                    Rs2Npc.interact(12223, "Attack");
                }
            }
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        if (VardorvisScript.inFight) {
            handleProjectile(event);
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (Objects.equals(event.getNpc().getName(), "Vardorvis") && inFight && inInstance) {
            Microbot.log("Vardorvis DEAD!!!!");

            VardorvisScript.inFight = false;
            vardorvisScript.inInstance = false;
            Rs2Prayer.toggleQuickPrayer(false);
            state = State.AFTER_FIGHT;
        }
    }

    private void specVardorvis() {
        int currentSpec = Rs2Combat.getSpecEnergy();
        walkingExecutor.submit(() -> {
            walkingExecutor.submit(() -> {
                Rs2Inventory.wield(27690);
                Rs2Combat.setSpecState(true);

                sleep(50);

                Rs2Npc.interact(12223, "Attack");


                sleepUntil(() -> Rs2Combat.getSpecEnergy() != currentSpec);

                Rs2Inventory.wield(29796);
            }, 50);
        }, 50);
    }

    private boolean isBloodSplatsVisible() {
        for (int widgetId : trackedWidgets) {
            if (Rs2Widget.isWidgetVisible(widgetId)) {
                Rs2Widget.findWidget("hi");
                return true;
            }
        }
        return false;
    }
    public void clickWidgetWithDelay(Widget widget, int delayMs) {
        executor.submit(() -> {
            try {
                Thread.sleep(delayMs);
                if (Rs2Widget.isWidgetVisible(widget.getId())) {
                    Microbot.log("Clicking on widget  | Current tick = " + currentRunningTicks);
                    Rs2Widget.clickWidget(widget);
                }
            } catch (InterruptedException e) {
                //Thread.currentThread().interrupt();
                //Microbot.log("Async delay interrupted for widget: " + widget.getId());
            }
        });
    }
    public void clickingBloodSplats() {
        for (int widgetId : trackedWidgets) {
            if (Rs2Widget.isWidgetVisible(widgetId)) {
                clickWidgetWithDelay(Rs2Widget.getWidget(widgetId), Rs2Random.between(30, 90));// 30, 120
            }
        }
    }
    public void handleProjectile(ProjectileMoved event) {
        final Projectile projectile = event.getProjectile();
        final int remainingCycles = projectile.getRemainingCycles();


        if (remainingCycles >= 10 && remainingCycles < 25) { // > 0
            VardorvisScript.isProjectileActive = true;

            if (projectile.getId() == RANGE_PROJECTILE && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE) && currentPrayer != Rs2PrayerEnum.PROTECT_RANGE && remainingCycles == 20) {// && remainingCycles == 30 was 10

                Microbot.log("Toggling Protect Range ON   | cycles = " + remainingCycles +  " | Current tick = " + currentRunningTicks);

                //currentPrayer = Rs2PrayerEnum.PROTECT_RANGE;

                walkingExecutor.submit(() -> {
                    Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
                });
            } else if (projectile.getId() == RANGE_PROJECTILE && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE) && currentPrayer != Rs2PrayerEnum.PROTECT_RANGE && remainingCycles == 10) {

                Microbot.log("Toggling Protect Range ON   | cycles = " + remainingCycles +  " | Current tick = " + currentRunningTicks);

                walkingExecutor.submit(() -> {
                    Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
                });
            }
        }
//        else if (remainingCycles == 0) {
//            Microbot.log("Vardorvis projectile stopped");
//            VardorvisScript.isProjectileActive = false;
//
//            walkingExecutor.submit(() -> {
//                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
//            });
//        }
        else if (remainingCycles >= 0 && remainingCycles <= 2 && isProjectileActive) {
            Microbot.log("Vardorvis projectile stopped");
            VardorvisScript.isProjectileActive = false;

            walkingExecutor.submit(() -> {
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
            }, 50);
        }
    }
    public void checkAxeLocations() {
        Rs2Npc.getNpcs(12227)
                .filter(npc -> npc.getLocalLocation().getX() == 8384 && npc.getLocalLocation().getY() == 5568)
                .forEach(this::handleAxeAtLocation
                );
    }
    public void handleAxeAtLocation(NPC axe) {
        if (oppositeAxe) {
            //Microbot.log("Found NPC with ID 12227 at " + axe.getLocalLocation());
            Microbot.log("Moving to avoid tile because opposite axe  | Current tick = " + currentRunningTicks);
            walkingExecutor.submit(() -> {
                Rs2Walker.walkFastCanvas(new WorldPoint(1131,3421,0));
            }, 50);

            oppositeAxe = false;
            oppositeAxeCounter = 0;
        }
    }
    public void handleHealingAndPrayer() {
        Microbot.log("Handling healing | Current tick = " + currentRunningTicks);

        int currentHealth = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int currentPrayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);

        if (currentPrayer < maxPrayer - 30) {
            Rs2Inventory.interact("prayer potion", "drink");
        }

        if (currentHealth < maxHealth - 45) {
            Microbot.doInvoke(new NewMenuEntry("Eat", Rs2Inventory.slot(385), 9764864, MenuAction.CC_OP.getId(), 2, 385, "Shark"), new Rectangle(1, 1));

            scheduler.schedule(() -> {
                Microbot.doInvoke(new NewMenuEntry("Eat", Rs2Inventory.slot(3144), 9764864, MenuAction.CC_OP.getId(), 2, 3144, "Cooked karambwan"), new Rectangle(1, 1));
            }, 30, TimeUnit.MILLISECONDS);
        }
    }

    protected void shutDown() {
        vardorvisScript.shutdown();
        overlayManager.remove(vardorvisOverlay);

        scheduler.shutdown();
        executor.shutdown();
        sleepUntilExecutor.shutdown();

        tickCounter = 0;
        oppositeAxeCounter = 0;
        currentRunningTicks = 0;
        currentPrayer = Rs2PrayerEnum.RAPID_HEAL;

        VardorvisScript.inFight = false;
        VardorvisScript.inInstance = false;
        VardorvisScript.state = State.UNKNOWN;
        VardorvisScript.bankState = StateBank.UNKNOWN;
        VardorvisScript.POHState = StatePOH.UNKNOWN;
        VardorvisScript.isProjectileActive = false;
    }
}
