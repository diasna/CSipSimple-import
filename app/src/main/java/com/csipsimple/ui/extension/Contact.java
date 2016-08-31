package com.csipsimple.ui.extension;

/**
 * Created by islamap.Inc on 4/24/15.
 * Copyright (c) 2014 Machikon. All rights reserved.
 */
public class Contact {

    private String name;

    private String number;

    public Contact() {
    }

    public Contact(String name, String number) {
        this.name = name;
        this.number = number;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }
}
