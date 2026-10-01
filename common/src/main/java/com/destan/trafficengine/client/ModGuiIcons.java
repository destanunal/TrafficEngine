package com.destan.trafficengine.client;
import com.destan.trafficengine.client.gui.GuiIcon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
public enum ModGuiIcons {
    TRAFFIC_LIGHT("traffic_light"),
    TRAM_TRAFFIC_LIGHT("tram_traffic_light"),
    TRAFFIC_LIGHT_1_LIGHT("traffic_light_one_light"),
    TRAFFIC_LIGHT_2_LIGHTS("traffic_light_two_lights"),
    TRAFFIC_LIGHT_3_LIGHTS("traffic_light_three_lights"),
    TRAFFIC_LIGHT_4_LIGHTS("traffic_light_four_lights"),
    TRAFFIC_LIGHT_NO_ICON("traffic_light_no_icon"),
    TRAFFIC_LIGHT_RIGHT("traffic_light_right"),
    TRAFFIC_LIGHT_LEFT("traffic_light_left"),
    TRAFFIC_LIGHT_UP("traffic_light_up"),
    TRAFFIC_LIGHT_UP_RIGHT("traffic_light_up_right"),
    TRAFFIC_LIGHT_UP_LEFT("traffic_light_up_left"),
    TRAFFIC_LIGHT_PEDESTRIAN("traffic_light_pedestrian"),
    TRAFFIC_LIGHT_BIKE("traffic_light_bike"),
    TRAFFIC_LIGHT_TRAM_ICON("traffic_light_tram_icon"),
    TRAFFIC_LIGHT_TRAM_RIGHT("traffic_light_tram_right"),
    TRAFFIC_LIGHT_TRAM_LEFT("traffic_light_tram_left"),
    COPY("copy"),
    PASTE("paste"),
    HELP("help"),
    CHECK("check"),
    CANCEL("cancel"),
    EDIT("edit"),
    ERASE("erase"),
    PICK("pick"),
    TEXT("text"),
    FILL("fill"),
    DELETE("delete"),
    PATTERN("pattern"),
    ADD("add"),
    ADD_BULLET("add_bullet"),
    SAVE("save"),
    OPEN("open"),
    WRITE_TO_FILE("write_to_file"),
    DISCARD_FILE("discard_file"),
    DELETE_WHITE("delete_white"),
    MOVE_DOWN("move_down"),
    MOVE_UP("move_up");


    private final String id;
    private final GuiIcon sprite;
    ModGuiIcons(String id) {
        this.id=id;
        int[] uv=switch(id) {
            case "traffic_light" -> new int[]{0,0};
            case "tram_traffic_light" -> new int[]{16,0};
            case "traffic_light_one_light" -> new int[]{32,0};
            case "traffic_light_two_lights" -> new int[]{48,0};
            case "traffic_light_three_lights" -> new int[]{64,0};
            case "traffic_light_four_lights" -> new int[]{80,0};
            case "traffic_light_no_icon" -> new int[]{0,16};
            case "traffic_light_right" -> new int[]{16,16};
            case "traffic_light_left" -> new int[]{32,16};
            case "traffic_light_up" -> new int[]{48,16};
            case "traffic_light_up_right" -> new int[]{64,16};
            case "traffic_light_up_left" -> new int[]{80,16};
            case "traffic_light_pedestrian" -> new int[]{96,16};
            case "traffic_light_bike" -> new int[]{112,16};
            case "traffic_light_tram_icon" -> new int[]{0,32};
            case "traffic_light_tram_right" -> new int[]{16,32};
            case "traffic_light_tram_left" -> new int[]{32,32};
            case "traffic_light_tram_straight" -> new int[]{48,32};
            case "copy" -> new int[]{0,48};
            case "paste" -> new int[]{16,48};
            case "help" -> new int[]{32,48};
            case "check" -> new int[]{48,48};
            case "cancel" -> new int[]{64,48};
            case "edit" -> new int[]{0,64};
            case "erase" -> new int[]{16,64};
            case "pick" -> new int[]{32,64};
            case "text" -> new int[]{48,64};
            case "fill" -> new int[]{64,64};
            case "delete" -> new int[]{80,64};
            case "pattern" -> new int[]{96,64};
            case "add" -> new int[]{112,64};
            case "add_bullet" -> new int[]{128,64};
            case "save" -> new int[]{144,64};
            case "open" -> new int[]{160,64};
            case "write_to_file" -> new int[]{176,64};
            case "discard_file" -> new int[]{192,64};
            case "delete_white" -> new int[]{0,80};
            case "move_down" -> new int[]{16,80};
            case "move_up" -> new int[]{32,80};
            default -> new int[]{0,0};
        };
        sprite=new GuiIcon(ResourceLocation.fromNamespaceAndPath("trafficengine","textures/gui/icons.png"),uv[0],uv[1],16,16,256,256);
    }
    public String getId() {return id;}
    public GuiIcon getAsSprite(int width,int height) {return sprite;}
    public void render(GuiGraphics g,int x,int y) {sprite.render(g,x,y,16,16);}
}
