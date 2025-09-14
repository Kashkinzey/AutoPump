package autopump;

import arc.input.InputProcessor;
import arc.math.geom.Point2;
import arc.math.geom.Vec2;
import mindustry.entities.units.BuildPlan;
import mindustry.mod.Mod;
import arc.Core;
import arc.Events;
import arc.input.KeyCode;
import arc.scene.ui.layout.Table;
import arc.scene.ui.ImageButton;
import arc.scene.event.InputListener;
import arc.scene.event.InputEvent;
import mindustry.game.EventType;
import mindustry.Vars;
import mindustry.world.Tile;
import mindustry.content.Blocks;
import mindustry.ui.Styles;
import mindustry.gen.Icon;
import static mindustry.Vars.*;

public class AutoPumpMod extends Mod {

    private boolean enabled;
    private Tile selectedTile;
    private Table directionTable;
    private ImageButton enableButton;
    private int currentRotate = 1;

    @Override
    public void init() {
        Core.app.post(() -> {
            ui.settings.addCategory("AutoPump", table -> {
                table.sliderPref("pattern-size", 20, 5, 100, 1, value -> value + " блоков");
                table.checkPref("show-toggle-button", true);
            });
        });

        enabled = false;
        directionTable = new Table(Styles.black3);
        buildDirectionTable();

        // Кнопка включения/выключения
        Vars.ui.hudGroup.fill(t -> {
            enableButton = (ImageButton)t.button(Icon.up, Styles.emptyTogglei, () -> {
                enabled = !enabled;
                directionTable.visible = false;
                enableButton.setChecked(enabled);
            }).get();
            enableButton.resizeImage(30f);
            enableButton.visible(() -> Core.settings.getBool("show-toggle-button", true));
            t.margin(5f);
            t.marginRight(155f);
            t.top().right();
        });

        // Обработка кликов по карте
        Events.on(EventType.TapEvent.class, event -> {
            if (enabled && event.tile.floor().liquidDrop != null) {
                selectedTile = event.tile;
                directionTable.visible = true;
                updateTablePosition();
            }
        });

        // Горячая клавиша H
        Core.scene.addListener(new InputListener() {
            public boolean keyDown(InputEvent event, KeyCode keyCode) {
                if (!Vars.state.isMenu() && !Vars.ui.chatfrag.shown() && !Vars.ui.schematics.isShown() &&
                        !Vars.ui.database.isShown() && !Vars.ui.consolefrag.shown() && !Vars.ui.content.isShown() &&
                        !Core.scene.hasKeyboard() && keyCode == KeyCode.h) {
                    enabled = !enabled;
                    directionTable.visible = false;
                    enableButton.setChecked(enabled);
                }
                return false;
            }
        });

        // Обработка кликов вне таблицы
        Core.input.addProcessor(new InputProcessor() {
            public boolean touchDown(int screenX, int screenY, int pointer, KeyCode button) {
                if (!directionTable.hasMouse() && directionTable.visible) {
                    directionTable.visible = false;
                }
                return false;
            }
            public boolean touchUp(int screenX, int screenY, int pointer, KeyCode button) { return false; }
            public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
            public boolean keyDown(KeyCode keycode) { return false; }
            public boolean keyUp(KeyCode keycode) { return false; }
            public boolean keyTyped(char character) { return false; }
            public boolean mouseMoved(int screenX, int screenY) { return false; }
            public boolean scrolled(float amountX, float amountY) { return false; }
        });
    }

    private void updateTablePosition() {
        if (selectedTile != null && directionTable.visible) {
            Vec2 screenPos = Core.camera.project(selectedTile.worldx(), selectedTile.worldy() + 1f);
            directionTable.setPosition(
                    screenPos.x - directionTable.getWidth() / 2f,
                    screenPos.y - directionTable.getHeight() / 2f,
                    4
            );
        }
    }

    private void buildDirectionTable() {
        directionTable.visible = false;

        directionTable.update(() -> {
            if (Vars.state.isMenu()) {
                directionTable.visible = false;
                return;
            }
            if (selectedTile != null && directionTable.visible) {
                updateTablePosition();
            }
        });

        // Кнопка ВВЕРХ (2)
        ((ImageButton)directionTable.button(Icon.up, Styles.defaulti, () -> {
            currentRotate = 2;
            placeBridgePattern();
            directionTable.visible = false;
        }).get()).resizeImage(30f);

        directionTable.row();

        // Кнопки ВЛЕВО (1), ОТМЕНА, ВПРАВО (3)
        Table middleRow = directionTable.table().get();
        ((ImageButton)middleRow.button(Icon.left, Styles.defaulti, () -> {
            currentRotate = 1;
            placeBridgePattern();
            directionTable.visible = false;
        }).get()).resizeImage(30f);

        ((ImageButton)middleRow.button(Icon.cancel, Styles.defaulti, () -> {
            directionTable.visible = false;
        }).get()).resizeImage(30f);

        ((ImageButton)middleRow.button(Icon.right, Styles.defaulti, () -> {
            currentRotate = 3;
            placeBridgePattern();
            directionTable.visible = false;
        }).get()).resizeImage(30f);

        directionTable.row();

        // Кнопка ВНИЗ (0)
        ((ImageButton)directionTable.button(Icon.down, Styles.defaulti, () -> {
            currentRotate = 0;
            placeBridgePattern();
            directionTable.visible = false;
        }).get()).resizeImage(30f);

        directionTable.pack();
        Core.scene.root.addChildAt(0, directionTable);
    }

    private int getPatternSize() {
        return Core.settings.getInt("pattern-size", 20);
    }

    private void placeBridgePattern() {
        if (player == null || player.unit() == null || selectedTile == null) return;

        int centerX = selectedTile.x;
        int centerY = selectedTile.y;
        int size = getPatternSize();

        Point2 config = switch (currentRotate) {
            case 0 -> new Point2(0, -4);
            case 1 -> new Point2(-4, 0);
            case 2 -> new Point2(0, 4);
            case 3 -> new Point2(4, 0);
            default -> new Point2(0, -4);
        };

        for (int x = centerX - size/2; x <= centerX + size/2; x++) {
            for (int y = centerY - size/2; y <= centerY + size/2; y++) {
                Tile tile = Vars.world.tile(x, y);
                if (tile == null) continue;

                boolean shouldPlaceBridge = currentRotate == 1 || currentRotate == 3 ?
                        isChessboardPositionHorrizont(x, y, centerX, centerY) :
                        isChessboardPosition(x, y, centerX, centerY);

                if (shouldPlaceBridge && canBuildBridgeHere(tile)) {
                    BuildPlan plan = new BuildPlan(x, y, 0, Blocks.bridgeConduit, config);
                    player.unit().addBuild(plan);
                } else if (canBuildHere(tile)) {
                    BuildPlan plan = new BuildPlan(x, y, 0, Blocks.mechanicalPump);
                    player.unit().addBuild(plan);
                }
            }
        }
    }

    private boolean isChessboardPosition(int x, int y, int centerX, int centerY) {
        int relX = x - centerX;
        int relY = y - centerY;
        return Math.abs(relX) % 4 == 0 && Math.abs(relY) % 2 == 0 ||
                Math.abs(relX) % 2 == 0 && Math.abs(relX) % 4 != 0 && Math.abs(relY) % 2 != 0;
    }

    private boolean isChessboardPositionHorrizont(int x, int y, int centerX, int centerY) {
        int relX = x - centerX;
        int relY = y - centerY;
        return Math.abs(relY) % 4 == 0 && Math.abs(relX) % 2 == 0 ||
                Math.abs(relY) % 2 == 0 && Math.abs(relY) % 4 != 0 && Math.abs(relX) % 2 != 0;
    }

    private boolean canBuildHere(Tile tile) {
        return tile != null && tile.block() == Blocks.air &&
                mindustry.world.Build.validPlace(Blocks.mechanicalPump, player.team(), tile.x, tile.y, 0);
    }

    private boolean canBuildBridgeHere(Tile tile) {
        if (tile == null || tile.block() != Blocks.air ||
                !mindustry.world.Build.validPlace(Blocks.bridgeConduit, player.team(), tile.x, tile.y, 0)) {
            return false;
        }

        // Проверяем, что рядом есть хотя бы один тайл для помпы
        int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};

        for (int[] dir : directions) {
            Tile nearbyTile = Vars.world.tile(tile.x + dir[0], tile.y + dir[1]);
            if (nearbyTile != null && nearbyTile.block() == Blocks.air &&
                    mindustry.world.Build.validPlace(Blocks.mechanicalPump, player.team(), nearbyTile.x, nearbyTile.y, 0)) {
                return true;
            }
        }
        return false;
    }
}