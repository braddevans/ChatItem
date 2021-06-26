package me.dadus33.chatitem.utils;


import com.github.steveice10.opennbt.tag.builtin.CompoundTag;

public class Item {
    private String id;
    private byte amount;
    private short data;
    private CompoundTag tag;

    public CompoundTag getTag() {
        return tag;
    }

    public void setTag(CompoundTag newTag) {
        this.tag = newTag;
    }

    public String getId() {
        return id;
    }

    public void setId(String newId) {
        this.id = newId;
    }

    public short getData() {
        return data;
    }

    public void setData(short newData) {
        data = newData;
    }

    public byte getAmount() {
        return amount;
    }

    public void setAmount(byte newAmount) {
        this.amount = newAmount;
    }
}
