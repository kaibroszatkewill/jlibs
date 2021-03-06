/**
 * JLibs: Common Utilities for Java
 * Copyright (C) 2009  Santhosh Kumar T <santhosh.tekuri@gmail.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */

package jlibs.xml.sax.async;

/**
 * @author Santhosh Kumar T
 */
final class QName{
    final String prefix;
    final String localName;
    final String name;
    final char chars[];
    final int hash;

    QName next;

    QName(int prefixLength, char[] buffer, int offset, int length, int hash, QName next){
        name = new String(buffer, offset, length);
        if(prefixLength==0){
            prefix = "";
            localName = name;
        }else{
            prefix = name.substring(0, prefixLength);
            localName = name.substring(prefixLength+1);
        }
        System.arraycopy(buffer, offset, chars=new char[length], 0, length);
        this.hash = hash;
        this.next = next;
    }
}
