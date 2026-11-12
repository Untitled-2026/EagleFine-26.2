package dev.eaglefine;

import java.util.Locale;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * In-game configuration page for EagleFine's client-side features.
 */
public final class EagleFineOptionsScreen extends Screen {
    private static final Component TITLE = Component.translatable("eaglefine.options.title");

    private final Screen lastScreen;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 33, 33);

    public EagleFineOptionsScreen(Screen lastScreen) {
        super(TITLE);
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(TITLE, this.font);

        EagleFineConfig config = EagleFine.getInstance().getConfig();
        LinearLayout settings = LinearLayout.vertical().spacing(8);
        settings.defaultCellSetting().alignHorizontallyCenter();

        settings.addChild(new StringWidget(
                Component.translatable("eaglefine.options.zoom_description"), this.font));

        ZoomStrengthSlider zoomSlider = new ZoomStrengthSlider(0, 0, 260, 20, config);
        settings.addChild(zoomSlider);

        CycleButton<Boolean> smoothZoomButton = CycleButton.onOffBuilder(config.isSmoothZoomEnabled())
                .create(Component.translatable("eaglefine.options.smooth_zoom"),
                        (button, enabled) -> config.setSmoothZoomEnabled(enabled));
        settings.addChild(smoothZoomButton);

        LinearLayout actions = LinearLayout.horizontal().spacing(8);
        actions.addChild(Button.builder(
                Component.translatable("eaglefine.options.controls"),
                button -> this.minecraft.gui.setScreen(new ControlsScreen(this, this.minecraft.options)))
                .width(150).build());
        actions.addChild(Button.builder(
                Component.translatable("eaglefine.options.reset"),
                button -> {
                    config.reset();
                    zoomSlider.setZoomFactor(config.getZoomFactor());
                    smoothZoomButton.setValue(config.isSmoothZoomEnabled());
                }).width(150).build());
        settings.addChild(actions);

        this.layout.addToContents(settings);
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .width(200).build());
        this.layout.visitWidgets(widget -> this.addRenderableWidget(widget));
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.lastScreen);
    }

    private static final class ZoomStrengthSlider extends AbstractSliderButton {
        private static final float MIN_ZOOM = 2.0F;
        private static final float MAX_ZOOM = 16.0F;

        private final EagleFineConfig config;

        private ZoomStrengthSlider(int x, int y, int width, int height, EagleFineConfig config) {
            super(x, y, width, height,
                    Component.empty(), toSliderValue(config.getZoomFactor()));
            this.config = config;
            this.updateMessage();
        }

        void setZoomFactor(float zoomFactor) {
            this.setValue(toSliderValue(zoomFactor));
        }

        private float getZoomFactor() {
            return MIN_ZOOM + (float)this.value * (MAX_ZOOM - MIN_ZOOM);
        }

        @Override
        protected void updateMessage() {
            String formattedZoom = String.format(Locale.ROOT, "%.1fx", this.getZoomFactor());
            this.setMessage(Component.translatable(
                    "eaglefine.options.zoom_strength", Component.literal(formattedZoom)));
        }

        @Override
        protected void applyValue() {
            this.config.setZoomFactor(this.getZoomFactor());
        }

        private static double toSliderValue(float zoomFactor) {
            return Math.max(0.0D, Math.min(1.0D,
                    (zoomFactor - MIN_ZOOM) / (MAX_ZOOM - MIN_ZOOM)));
        }
    }
}
