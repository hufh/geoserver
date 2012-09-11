package org.geoserver.script.js.engine;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.tools.shell.Global;

public class CommonJSEngine extends AbstractScriptEngine implements Invocable {

    private CommonJSEngineFactory factory;
    
    public CommonJSEngine() {
        this(new CommonJSEngineFactory(null));
    }

    public CommonJSEngine(CommonJSEngineFactory factory) {
        this.factory = factory;
    }
    
    @Override
    public Bindings createBindings() {
        return new SimpleBindings();
    }
    
    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {
        if (script == null) {
            throw new NullPointerException("Null script");
        }
        return eval(new StringReader(script) , context);
    }
    
    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        Bindings bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
        String filename = null;
        if (context != null && bindings != null) {
            filename = (String) bindings.get(ScriptEngine.FILENAME);
        }
        if (filename == null) {
            filename = (String) get(ScriptEngine.FILENAME);
        }
        
        filename = filename == null ? "<Unknown source>" : filename;
        Object result;
        EngineScope scope = getRuntimeScope(context);
        Context cx = enterContext();
        try {
            result = cx.evaluateReader(scope, reader, filename, 1, null);
        } catch (IOException e) {
            throw new ScriptException(e);
        } finally {
            Context.exit();
        }
        return result;
    }
    
    @Override
    public ScriptEngineFactory getFactory() {
        return factory;
    }
    
    private Global getGlobal() {
        return factory.getGlobal();
    }

    private EngineScope getRuntimeScope(ScriptContext context) {
        EngineScope scope = new EngineScope(context);
        Global global = getGlobal();
        scope.setParentScope(global);
        scope.setPrototype(global);
        Context cx = enterContext();
        try {
            scope.put("exports", scope, cx.newObject(global));
        } finally {
            Context.exit();
        }
        return scope;
    }

    @Override
    public <T> T getInterface(Class<T> cls) {
        // TODO implement this
        throw new RuntimeException("getInterface not implemented");
    }

    @Override
    public <T> T getInterface(Object object, Class<T> cls) {
        // TODO implement this
        throw new RuntimeException("getInterface not implemented");
    }

    @Override
    public Object invokeFunction(String name, Object... args)
            throws ScriptException, NoSuchMethodException {
        return invokeMethod(null, name, args);
    }

    @Override
    public Object invokeMethod(Object thisObj, String name, Object... args)
            throws ScriptException, NoSuchMethodException {
        if (name == null) {
            throw new NullPointerException("Method name is null");
        }
        EngineScope engineScope = getRuntimeScope(context);
        if (thisObj == null) {
            thisObj = engineScope;
        } else {
            if (!(thisObj instanceof Scriptable)) {
                thisObj = Context.toObject(thisObj, getGlobal());
            }
        }
        Object methodObj = ScriptableObject.getProperty((Scriptable) thisObj, name);
        if (!(methodObj instanceof Function)) {
            throw new NoSuchMethodException("No such method: " + name);
        }
        Function method = (Function) methodObj;
        Scriptable scope = method.getParentScope();
        if (scope == null) {
            scope = engineScope;
        }
        Context cx = enterContext();
        Object result;
        try {
            result = method.call(cx, scope, (Scriptable) thisObj, args);
        } catch (JavaScriptException jse) {
            int line = (line = jse.lineNumber()) == 0 ? -1 : line;
            Object value = jse.getValue();
            String str = (value != null && value.getClass().getName().equals("org.mozilla.javascript.NativeError") ?
                          value.toString() :
                          jse.toString());
            throw new ExtendedScriptException(jse, str, jse.sourceName(), line);
        } catch (RhinoException re) {
            int line = (line = re.lineNumber()) == 0 ? -1 : line;
            throw new ExtendedScriptException(re, re.toString(), re.sourceName(), line);
        } finally {
            Context.exit();
        }
        return result;
    }
    
    /**
     * Associate a context with the current thread.  This calls Context.enter()
     * and sets the language version to 1.8.
     * @return a Context associated with the thread
     */
    public static Context enterContext() {
        Context cx = Context.enter();
        cx.setLanguageVersion(Context.VERSION_1_8);
        return cx;
    }


}
