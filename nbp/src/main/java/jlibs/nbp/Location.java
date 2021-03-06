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

package jlibs.nbp;

/**
 * @author Santhosh Kumar T
 */
public class Location{
    private int line, col, offset;
    public Location(){
        reset();
    }

    public int getLineNumber(){ return line; }
    public int getColumnNumber(){ return col; }
    public int getCharacterOffset(){ return offset; }
    public void set(Location that){
        this.line = that.line;
        this.col = that.col;
        this.offset = that.offset;
        this.skipLF = that.skipLF;
    }

    private boolean skipLF;

    /**
     * return value tells whether the given character
     * has been included in location or not
     *
     * for example in sequence "\r\n", the character
     * '\n' is not included in location.
     */
    public boolean consume(int ch){
        offset++;
        if(ch==0x0D){
            skipLF = true;
            line++;
            col = 0;
            return true;
        }else if(ch==0x0A){
            if(skipLF){
                skipLF = false;
                return false;
            }else{
                line++;
                col = 0;
                return true;
            }
        }else{
            skipLF = false;
            col++;
            return true;
        }
    }

    public void reset(){
        line = col = offset = 0;
        skipLF = false;
    }

    @Override
    public String toString(){
        return line+":"+col;
    }
}
