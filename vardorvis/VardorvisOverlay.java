package net.runelite.client.plugins.microbot.vardorvis;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class VardorvisOverlay extends OverlayPanel {

    @Inject
    VardorvisOverlay(VardorvisPlugin plugin)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Vardorvis SLAYER V1.0.0")
                    .color(Color.red.darker())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Opposite axe = " + VardorvisPlugin.oppositeAxe)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Opposite axe counter = " + VardorvisPlugin.oppositeAxeCounter)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Current ticks = " + VardorvisPlugin.currentRunningTicks)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Is projectile active = " + VardorvisScript.isProjectileActive)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("In fight = " + VardorvisScript.inFight)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("In instance = " + VardorvisScript.inInstance)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State = " + VardorvisScript.state)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Bank State = " + VardorvisScript.bankState)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("POH State = " + VardorvisScript.POHState)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Prayer = " + VardorvisPlugin.currentPrayer)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Axe on safe tile = " + VardorvisPlugin.axeOnSafeTile)
                    .build());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
