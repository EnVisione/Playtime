package com.enviouse.sef;


import com.enviouse.sef.events.ChatEventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.enviouse.sef.config.PermissionsHandler;

public final class TextFormatter {
	public static final String RESET_ALL_FORMAT = "&r";
	
	public static final String BOLD_FORMAT          = "&l";
	public static final String UNDERLINE_FORMAT     = "&n";
	public static final String ITALIC_FORMAT        = "&o";
	public static final String OBFUSCATED_FORMAT    = "&k";
	public static final String STRIKETHROUGH_FORMAT = "&m";
	
	public static final String COLOR_BLACK =        "&0";
	public static final String COLOR_DARK_BLUE =    "&1";
	public static final String COLOR_DARK_GREEN =   "&2";
	public static final String COLOR_DARK_AQUA =    "&3";
	public static final String COLOR_DARK_RED =     "&4";
	public static final String COLOR_DARK_PURPLE =  "&5";
	public static final String COLOR_GOLD =         "&6";
	public static final String COLOR_GRAY =         "&7";
	public static final String COLOR_DARK_GRAY =    "&8";
	public static final String COLOR_BLUE =         "&9";
	public static final String COLOR_GREEN =        "&a";
	public static final String COLOR_AQUA =         "&b";
	public static final String COLOR_RED =          "&c";
	public static final String COLOR_LIGHT_PURPLE = "&d";
	public static final String COLOR_YELLOW =       "&e";
	public static final String COLOR_WHITE =        "&f";

	private static final Pattern HEX_PATTERN = Pattern.compile("#([0-9a-fA-F]{6})");

	public static MutableComponent stringToFormattedText(String msg) {
		return stringToFormattedText(msg, true, true);
	}
	public static MutableComponent stringToFormattedText(String msg, boolean enableColors, boolean enableStyles) {
		return stringToFormattedText(msg, enableColors, enableStyles, null);
	}

	public static MutableComponent stringToFormattedText(String msg, boolean enableColors, boolean enableStyles, UUID permissionUuid) {
        if(msg == null) return null;
        Style defaultStyle = Style.EMPTY;
        String DefaultColor = ChatEventHandler.getChatMessageColor();
        ServerEssentialsForge.LOGGER.debug("default Color: {}", DefaultColor);
        if (!DefaultColor.isEmpty()) {
            ChatFormatting cfg = ChatFormatting.getByName(DefaultColor);
            if(cfg != null) defaultStyle = defaultStyle.withColor(TextColor.fromLegacyFormat(cfg));
        }
        Style curStyle = defaultStyle;
        MutableComponent newMsg = Component.empty();
        boolean nextIsStyle = false;
        StringBuilder curStr = new StringBuilder();
        byte curMask = 0;

        for(int i = 0; i < msg.length(); i++) {
            char ch = msg.charAt(i);
            if(ch == '&') {
                if(nextIsStyle) {
                    nextIsStyle = false;
                    curStr.append("&");
                } else nextIsStyle = true;
                continue;
            }

            if(nextIsStyle) {
                if(curStr.length() > 0) {
                    MutableComponent tmp = BitwiseStyling.makeEncapsulatingTextComponent(curStr.toString(), enableStyles ? curMask : 0);
                    tmp.withStyle(curStyle);
                    newMsg.append(tmp);
                    curStr = new StringBuilder();
                }

                if(ch == '#') {
                    int start = i + 1;
                    int end = start + 6;
                    if(end <= msg.length()) {
                        String hexStr = msg.substring(start, end);
                        Matcher m = HEX_PATTERN.matcher("#" + hexStr);
                        if(m.matches() && enableColors && (permissionUuid == null || PermissionsHandler.playerHasPermission(permissionUuid, PermissionsHandler.hexChatNode))) {
                            curStyle = curStyle.withColor(Integer.parseInt(hexStr, 16));
                            i = end - 1; // skip over the hex digits consumed
                            // leave styles unchanged
                            nextIsStyle = false;
                            continue;
                        }
                    }
                }

                if(enableColors) {
                    Style next = applyLegacyColor(ch, curStyle, permissionUuid);
                    if(next != null) curStyle = next;
                }
                if(ch == 'r') {
                    curStyle = defaultStyle;
                    curMask = 0;
                } else curMask |= BitwiseStyling.getStyleBit(ch);
                nextIsStyle = false;
            } else {
                curStr.append(ch);
            }
        }

        if(curStr.length() > 0) {
            MutableComponent tmp = BitwiseStyling.makeEncapsulatingTextComponent(curStr.toString(), enableStyles ? curMask : 0);
            tmp.withStyle(curStyle);
            newMsg.append(tmp);
        }
        return newMsg;
    }

    private static Style applyLegacyColor(char c, Style cur, UUID permissionUuid) {
        ChatFormatting fmt = switch (c) {
            case '0' -> ChatFormatting.BLACK;
            case '1' -> ChatFormatting.DARK_BLUE;
            case '2' -> ChatFormatting.DARK_GREEN;
            case '3' -> ChatFormatting.DARK_AQUA;
            case '4' -> ChatFormatting.DARK_RED;
            case '5' -> ChatFormatting.DARK_PURPLE;
            case '6' -> ChatFormatting.GOLD;
            case '7' -> ChatFormatting.GRAY;
            case '8' -> ChatFormatting.DARK_GRAY;
            case '9' -> ChatFormatting.BLUE;
            case 'a' -> ChatFormatting.GREEN;
            case 'b' -> ChatFormatting.AQUA;
            case 'c' -> ChatFormatting.RED;
            case 'd' -> ChatFormatting.LIGHT_PURPLE;
            case 'e' -> ChatFormatting.YELLOW;
            case 'f', 'r' -> ChatFormatting.WHITE;
            default -> null;
        };
        if(fmt == null) return cur;
        if(permissionUuid != null && !PermissionsHandler.playerHasColorPermission(permissionUuid, c)) return cur;
        return cur.withColor(TextColor.fromLegacyFormat(fmt));
    }

	public static String removeTextFormatting(String msg) {
		if(msg == null) return null;
		StringBuilder newMsg = new StringBuilder();
        StringBuilder curStr = new StringBuilder();
        boolean nextIsStyle = false;
		for(int i = 0; i < msg.length(); i++) {
			char c = msg.charAt(i);
			if(c == '&') {
				if(nextIsStyle) {
					nextIsStyle = false;
					curStr.append("&");
				} else nextIsStyle = true;
			} else if(nextIsStyle) {
				if(isColorOrStyle(c)) {
					newMsg.append(curStr);
					curStr = new StringBuilder();
				} else curStr.append("&").append(c);
				nextIsStyle = false;
			} else curStr.append(c);
		}
		if(!curStr.isEmpty())
			newMsg.append(curStr);
		return newMsg.toString();
	}

	public static boolean messageContainsColorsOrStyles(String msg, boolean checkColors) {
		boolean checkNext = false;
		for(int i = 0; i < msg.length(); i++) {
			char c = msg.charAt(i);
			if(c == '&') {
                checkNext = !checkNext;
			} else if(checkNext) {
				if(checkColors) { if(isColor(c)) return true; }
				else { if(isStyle(c)) return true; }
			}
		}
		return false;
	}
	public static String colorString() {
		return """
                       &fLight:  &c&&c &e&&e &9&&9 &a&&a &b&&b &d&&d &f&&f &7&&7
                       &fDark:   &4&&4 &6&&6 &1&&1 &2&&2 &3&&3 &5&&5 &0&&0 &8&&8
                       &fStyles: &l&&l&r &n&&n&r &o&&o&r &m&&m&r &k&&k&r
                       """;
	}

    private static boolean isColorOrStyle(char c) {
        return isColor(c) || isStyle(c);
    }
	private static boolean isColor(char c) {
		if(c >= '0' && c <= '9') return true;
        return c >= 'a' && c <= 'f';
    }
    private static boolean isStyle(char c) {
     if(c >= 'k' && c <= 'o') return true;
        return c == 'r';
    }
}
