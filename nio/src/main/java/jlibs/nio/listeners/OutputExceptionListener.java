/*
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

package jlibs.nio.listeners;

import jlibs.nio.Output;

/**
 * @author Santhosh Kumar Tekuri
 */
public interface OutputExceptionListener{
    public void process(Output out, Throwable thr);

    public static final OutputExceptionListener DO_NOTHING = (out, thr) -> { };
    public static final OutputExceptionListener CLOSE_OUTPUT = (out, thr) -> { CloseOutput.start(out, null, null); };
    public static final OutputExceptionListener SHUTDOWN_OUTPUT_CHANNEL = (out, thr) -> ShutdownChannel.start(out.channel());
}
