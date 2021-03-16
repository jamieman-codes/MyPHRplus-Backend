package com.jws1g18.myphrplus;

import java.io.Serializable;

public class EncFile implements Serializable{
    byte[] aesBuf;
    byte[] cphBuf;

    public EncFile(byte[] aesBuf, byte[] cphBuf){
        this.aesBuf = aesBuf;
        this.cphBuf = cphBuf;
    }
}
