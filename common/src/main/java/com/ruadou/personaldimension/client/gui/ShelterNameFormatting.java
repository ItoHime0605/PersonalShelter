package com.ruadou.personaldimension.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Safe legacy ampersand formatting for player-defined shelter names. */
public final class ShelterNameFormatting {
    private ShelterNameFormatting() {}
    public static Component parse(String input) {
        MutableComponent result = Component.empty();
        if (input == null) return result;
        StringBuilder part = new StringBuilder();
        net.minecraft.network.chat.Style style = net.minecraft.network.chat.Style.EMPTY;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '&' && i + 1 < input.length()) {
                ChatFormatting code = ChatFormatting.getByCode(input.charAt(i + 1));
                if (code != null) {
                    if (part.length() > 0) { result.append(Component.literal(part.toString()).setStyle(style)); part.setLength(0); }
                    if (code == ChatFormatting.RESET) style = net.minecraft.network.chat.Style.EMPTY;
                    else if (code.isColor()) style = net.minecraft.network.chat.Style.EMPTY.applyFormat(code);
                    else style = style.applyFormat(code);
                    i++;
                    continue;
                }
            }
            if (c >= 32 && c != 127 && c != '§') part.append(c);
        }
        if (part.length() > 0) result.append(Component.literal(part.toString()).setStyle(style));
        return result;
    }
}
