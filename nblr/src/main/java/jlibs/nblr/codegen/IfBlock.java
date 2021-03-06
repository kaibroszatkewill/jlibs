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

package jlibs.nblr.codegen;

import jlibs.core.annotation.processing.Printer;
import jlibs.core.lang.StringUtil;
import jlibs.nblr.actions.ErrorAction;
import jlibs.nblr.actions.EventAction;
import jlibs.nblr.actions.PublishAction;
import jlibs.nblr.codegen.java.SyntaxClass;
import jlibs.nblr.matchers.Any;
import jlibs.nblr.matchers.Matcher;
import jlibs.nblr.matchers.Range;
import jlibs.nblr.rules.*;
import jlibs.nbp.NBParser;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static jlibs.core.annotation.processing.Printer.MINUS;
import static jlibs.core.annotation.processing.Printer.PLUS;

/**
 * @author Santhosh Kumar T
 */
public class IfBlock implements Iterable<IfBlock>{
    public Matcher matcher;
    public Path path;
    protected int common = -1;

    public final State state;
    public final RootIf root;
    public IfBlock parent;
    public final List<IfBlock> children = new ArrayList<IfBlock>();

    public IfBlock(State state){
        this.state = state;
        root = state.rootIF==null ? (RootIf)this : state.rootIF;
    }

    @Override
    public Iterator<IfBlock> iterator(){
        return new Iterator<IfBlock>(){
            IfBlock block;

            private IfBlock getNext(){
                if(block==null)
                    return IfBlock.this;
                if(block.children.size()>0){
                    return block.children.get(0);
                }else{
                    IfBlock block = this.block;
                    IfBlock next = null;
                    while(block!=null){
                        List<IfBlock> siblings = block.siblings();
                        int index = siblings.indexOf(block);
                        if(index+1<siblings.size()){
                            next = siblings.get(index+1);
                            break;
                        }
                        block = block.parent;
                    }
                    return next;
                }
            }

            @Override
            public boolean hasNext(){
                return getNext()!=null;
            }

            @Override
            public IfBlock next(){
                return block = getNext();
            }

            @Override
            public void remove(){
                throw new UnsupportedOperationException();
            }
        };
    }

    protected InputType analalizeInput(){
        InputType inputType = new InputType();
        for(IfBlock block: this){
            if(block.matcher!=null){
                inputType.codePoint |= block.matcher.clashesWith(Range.SUPPLIMENTAL);
                inputType.character |= block.matcher.clashesWith(Range.NON_SUPPLIMENTAL);
                inputType.newLine |= block.matcher.clashesWith(Any.NEW_LINE);
            }
            if(block.path!=null && block.path.matcher()!=null)
                inputType.consumes = true;
        }
        return inputType;
    }

    private static String condition(Matcher matcher, String ch){
        String condition = matcher._javaCode(ch);
        if(matcher.checkFor(NBParser.EOF)){
            if(matcher.name==null)
                condition = '('+condition+')';
            condition = "ch!=EOF && "+condition;
        }
        return condition;
    }

    private int depth(){
        int depth = 0;
        IfBlock parent = this.parent;
        while(parent!=null){
            parent = parent.parent;
            depth++;
        }
        return depth;
    }

    public int height(){
        return height(this);
    }
    
    private static int height(IfBlock block){
        int height = 1;

        int depth = 1;
        while(true){
            if(block.children.size()>0){
                depth++;
                height = Math.max(height, depth);
                block = block.children.get(0);
            }else{
                IfBlock next = null;
                while(block.parent!=null){
                    int index = block.parent.children.indexOf(block);
                    if(index+1<block.parent.children.size()){
                        next = block.parent.children.get(index+1);
                        break;
                    }
                    depth--;
                    block = block.parent;
                }
                if(next==null)
                    return height;
                block = next;
            }
        }
    }

    public List<IfBlock> siblings(){
        return parent==null ? state.ifBlocks : parent.children;
    }

    private String blockStatement(boolean heightDecreased){
        List<IfBlock> siblings = siblings();
        int index = siblings.indexOf(this);
        if(state.ifBlocks.get(0).usesFinishAll())
            index--;

        String blockStmt = "";
        if(index!=0 && !root.lookAheadRequired())
            blockStmt = "else";
        if(matcher!=null){
            if(blockStmt.length()>0)
                blockStmt += " ";
            String ch = "ch";
            if(heightDecreased || !children.isEmpty()){
                int depth = depth();
                if(state.rootIF.lookAheadChars()){
                    if(!heightDecreased)
                        ch = depth==0 ?  "input[position]" : "input[position+"+depth+"]";
                }else
                    ch = "la["+depth+"]";
            }
            blockStmt += "if("+condition(matcher, ch)+")";
        }
        return blockStmt.length()==0 ? null : blockStmt;
    }

    protected void generateChildren(Printer printer, State next){
        if(common!=-1)
            children.get(0).travelPath(printer, 0, common);
        for(IfBlock child: children)
            child.generate(printer, next);
    }
    
    public void generate(Printer printer, State next){
        if(usesFinishAll()){
            useFinishAll(printer);
            return;
        }

        boolean closeLaLenCheck = false;
        boolean heightDecreased = false;
        if(parent==null){
            int index = state.ifBlocks.indexOf(this);
            int prevHeight = index==0 ? 1 : state.ifBlocks.get(index-1).height();
            int curHeight = height();

            if(curHeight>prevHeight){
                if(prevHeight==1){
                    if(root.lookAheadChars())
                        printer.printlns("int "+state.rootIF.available()+" = limit-position+(marker==EOF ? 1 : 0);");
                    else
                        printer.println("addToLookAhead(ch);");
                }

                if(!root.lookAheadChars())
                    root.fillLookAhead(printer, prevHeight, curHeight);
                closeLaLenCheck = true;
                if(root.lookAheadChars()){
                    String last = "position+"+(curHeight-1);
                    printer.printlns(
                        "if("+root.available()+">="+curHeight+"){",
                            PLUS,
                            "ch = limit=="+last+" ? EOF : input["+last+"];"
                    );
                }else{
                    printer.printlns(
                        "if(laLen=="+curHeight+"){",
                            PLUS
                    );
                }
            }else{
                heightDecreased = curHeight<prevHeight;
                if(heightDecreased && matcher!=null && root.lookAheadChars())
                    printer.println("ch = limit==position ? marker : input[position];");
            }
        }


        List<IfBlock> siblings = siblings();
        String blockStmt = blockStatement(heightDecreased);
        if(blockStmt!=null){
            printer.printlns(
                blockStmt+"{",
                    PLUS
            );
        }

        if(path!=null)
            generateBody(printer, next, heightDecreased);
        else
            generateChildren(printer, next);
        
        boolean addExpected = parent == null && siblings.indexOf(this) == siblings.size() - 1 && matcher != null;
        if(blockStmt!=null){
            printer.printlns(MINUS);
            printer.print("}");

            int index = siblings.indexOf(this);
            IfBlock nextIF = index==siblings.size()-1 ? null : siblings.get(index+1);
            String nextBlockStmt = nextIF==null ? null : nextIF.blockStatement(heightDecreased);
            boolean sameLine;
            if(nextBlockStmt==null)
                sameLine = addExpected && !root.lookAheadRequired();
            else
                sameLine = nextBlockStmt.startsWith("else");
            if(!sameLine)
                printer.println();
        }
        if(closeLaLenCheck){
            printer.printlns(MINUS);
            printer.print("}");
            if(root.lookAheadChars()){
                printer.printlns(
                    "else if(marker==EOC)",
                        PLUS,
                        state.breakStatement(),
                        MINUS
                );
            }else
                printer.println();
        }
        
        if(addExpected){
            if(!root.lookAheadRequired())
                printer.print("else ");
            printer.println("throw expected(ch, \""+ StringUtil.toLiteral(state.expected(), false)+"\");");
        }
    }

    private Edge edgeWithRule(){
        for(Object obj : path){
            if(obj instanceof Edge){
                Edge edge = (Edge) obj;
                if(edge.ruleTarget != null)
                    return edge;
            }
        }
        return null;
    }

    private String ruleID(Edge edgeWithRule){
        String ruleName = edgeWithRule.ruleTarget.rule.name;
        if(!SyntaxClass.DEBUGGABLE && Node.DYNAMIC_STRING_MATCH.equals(edgeWithRule.source.name))
            ruleName = "DYNAMIC_STRING_MATCH";
        return "RULE_"+ruleName.toUpperCase();
    }

    private String methodCall(Edge edgeWithRule){
        int id = edgeWithRule.ruleTarget.node().stateID;
        
        String methodCall;
        if(!SyntaxClass.DEBUGGABLE && Node.DYNAMIC_STRING_MATCH.equals(edgeWithRule.source.name))
            methodCall = "matchString("+id+", dynamicStringToBeMatched)";
        else{
            Rule rule = edgeWithRule.ruleTarget.rule;
            if(rule.id<0)
                methodCall = "matchString(RULE_"+rule.name.toUpperCase()+", "+id+", STRING_IDS[-RULE_"+rule.name.toUpperCase()+"])";
            else
                methodCall = rule.name+"("+id+")";
        }
        return methodCall;
    }

    private void setState(Printer printer, Node node){
        if(path.get(0)!=node)
            printer.println("state = "+node.stateID+";");
    }

    private boolean addContinue(State next, Node returnNode){
        return root.lookAheadRequired() || next==null || returnNode!=next.fromNode;
    }
    
    private void generateBody(Printer printer, State next, boolean heightDecreased){
        int common = parent==null ? root.common : parent.common;
        int check = travelPath(printer, common+1, path.size()-1);

        if(check==CHECK_ERROR)
            return;
        
        if(parent!=null || heightDecreased){
            if(!root.lookAheadChars())
                printer.println("resetLookAhead();");
        }

        Edge edgeWithRule = edgeWithRule();

        if(edgeWithRule==null){

            Node returnNode = (Node)path.get(path.size()-1);
            if(returnNode.outgoing.size()==0){
                printer.println("return "+(check==CHECK_STOP?"!stop":"true")+";");
            }else{
                setState(printer, returnNode);
                if(check==CHECK_STOP)
                    printer.printlnIf("stop", state.breakStatement());
                else if(check==CHECK_POP)
                    printer.printlnIf("pop", "return true;");
                if(addContinue(next, returnNode))
                    printer.println("continue;");
            }
        }else{
            Node returnNode = edgeWithRule.target;
            boolean doReturn = new Routes(state.ruleMethod.rule, returnNode).isEOF();
            if(!doReturn)
                setState(printer, returnNode);

            if(check==CHECK_STOP){
                printer.printlns(
                    "if(stop){",
                        PLUS,
                        "exiting("+ruleID(edgeWithRule)+", "+edgeWithRule.ruleTarget.node().stateID+");",
                        doReturn ? "return false;" : state.breakStatement(),
                        MINUS,
                    "}else"
                );
            }else if(check==CHECK_POP)
                printer.printlnIf("pop", "return true;");

            if(doReturn){
                printer.println("return "+methodCall(edgeWithRule)+";");
            }else{
                List<String> ifBody = new ArrayList<String>();
                if(addContinue(next, returnNode))
                    ifBody.add("continue;");

                List<String> elseBody = new ArrayList<String>();
                elseBody.add(state.breakStatement());

                if(ifBody.size()==0)
                    printer.printlnIf("!"+methodCall(edgeWithRule), elseBody);
                else
                    printer.printlnIf(methodCall(edgeWithRule), ifBody, elseBody);
            }
        }
    }

    private static int CHECK_STOP = 1;
    private static int CHECK_POP = 2;
    private static int CHECK_ERROR = 3;
    public int travelPath(Printer printer, int from, int to){
        int check = 0;

        for(int index=0; index<path.size(); index++){
             Object obj = path.get(index);
            if(obj instanceof Node){
                Node node = (Node)obj;

                if(index<path.size()-1 || node.outgoing.size()==0){ // !lastNode || sinkNode
                    if(node.action!=null){
                        if(index>=from && index<=to){
                            if(SyntaxClass.DEBUGGABLE)
                                printer.println("handler.execute("+state.ruleMethod.rule.id+", "+node.id+");");
                            else
                                printer.println(node.action.javaCode()+';');
                        }
                        if(node.action instanceof EventAction || node.action instanceof PublishAction){
                            if(node.action.toString().startsWith("#"))
                                check = CHECK_STOP;
                            else if(node.action.toString().startsWith("!"))
                                check = CHECK_POP;
                        }else if(node.action instanceof ErrorAction)
                            return CHECK_ERROR;
                    }
                }
            }else if(obj instanceof Edge){
                if(!(index>=from && index<=to))
                    continue;
                Edge edge = (Edge)obj;
                if(edge.ruleTarget!=null){
//                    RuleTarget ruleTarget = edge.ruleTarget;
//                    int idAfterRule = idAfterRule(edge);
//                    String ruleName = ruleName(edge);
//                    printer.println("push(RULE_"+ruleName.toUpperCase()+", "+idAfterRule+", "+id(ruleTarget.node())+");");
                }else if(edge.matcher!=null){
                    if(parent==null){
                        if(!matcher.clashesWith(Range.SUPPLIMENTAL) && !matcher.clashesWith(Any.NEW_LINE))
                            consumeDirectly(printer, edge);
                        else
                            printer.println("consume(ch);"); //"+edge.source.buffering);
                    }else{
                        if(root.lookAheadChars())
                            consumeDirectly(printer, edge);
                        else
                            printer.println("consume(FROM_LA);"); //"+edge.source.buffering);
                    }
                }
            }
        }
        return check;
    }

    private void consumeDirectly(Printer printer, Edge edge){
        if(edge.source.buffering== Answer.NO)
            printer.println("position++;");
        else if(edge.source.buffering==Answer.YES)
            printer.println("buffer.append(input[position++]);");
        else{
            printer.printlns(
                "if(buffer.isBuffering())",
                    PLUS,
                    "buffer.append(input[position]);",
                    MINUS,
                "position++;"
            );
        }
    }

    /*-------------------------------------------------[ FinishAll ]---------------------------------------------------*/
    
    public boolean usesFinishAll(){
        return parent==null && state.ifBlocks.get(0)==this && path!=null && path.size()>1 && edgeWithRule()==null && path.get(0)==path.get(path.size()-1);
    }

    public boolean finishAllReturnValueRequired(){
        for(int i=1; i<state.ifBlocks.size(); i++){
            IfBlock sibling = state.ifBlocks.get(i);
            if(sibling.matcher!=null){
                return true;
            }
        }
        return false;
    }
    
    private void useFinishAll(Printer printer){
        FinishAllMethod method = state.ruleMethod.syntaxClass.addToFinishAll(matcher);
        method.use(printer, state, finishAllReturnValueRequired());
    }
}

class InputType{
    boolean codePoint;
    boolean character;
    boolean newLine;
    boolean consumes;

    public boolean characterOnly(){
        return character && !codePoint;
    }
}
