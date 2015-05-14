package com.yliec.contactasync;

import java.util.HashMap;

/**
 * Created by Lecion on 4/1/15.
 */
public class Contact {
    private HashMap<String, String> contact;

    public Contact() {
        contact = new HashMap<>();
    }

    public Contact(String name,  String phone) {
        contact = new HashMap<>();
        addPhone(name, phone);
    }

    public void addPhone(String name, String phone) {
        contact.put(name, phone);
    }

    public HashMap<String, String> getContact() {
        return contact;
    }


}
