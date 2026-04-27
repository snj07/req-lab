package com.reqlab.core.scripting

/**
 * Builds a self-contained JavaScript program that:
 *  1. Injects response / request / variable context as JS values
 *  2. Defines the `reqlab` (or custom prefix) namespace with
 *     Postman-compatible APIs: test, expect, response, request,
 *     environment, globals, collectionVariables, variables, console
 *  3. Executes the user script inside a try-catch
 *  4. Returns all results (assertions, logs, mutations, variables) as a JSON string
 *
 * The returned string is fed into [evaluateJs] on each platform.
 */
internal object ScriptBootstrap {

    /**
     * Build a complete JavaScript IIFE that, when evaluated, returns a JSON
     * string containing test results, logs, variable changes, and mutations.
     */
    fun build(
        userScript: String,
        context: ScriptContext,
        prefix: String,
    ): String = buildString {
        // ── Opening IIFE ──────────────────────────────────────────────────
        append("(function(){")
        append("'use strict';")

        // ── Inject context data as JS vars ────────────────────────────────
        append("var __sc=").append(context.statusCode ?: "null").append(";")
        append("var __st=").append(jsString(statusText(context.statusCode))).append(";")
        append("var __rb=").append(jsString(context.responseBody)).append(";")
        append("var __rt=").append(context.responseTimeMs ?: "null").append(";")
        append("var __rs=").append(context.responseSizeBytes ?: "null").append(";")
        append("var __rh=").append(jsObject(context.responseHeaders)).append(";")
        append("var __ru=").append(jsString(context.url)).append(";")
        append("var __rm=").append(jsString(context.method)).append(";")
        append("var __rqb=").append(jsString(context.requestBody)).append(";")
        append("var __rqh=").append(jsObject(context.requestHeaders)).append(";")
        append("var __rqp=").append(jsObject(context.requestQueryParams)).append(";")
        append("var __ev=").append(jsObject(context.variables)).append(";")
        append("var __gv=").append(jsObject(context.globalVariables)).append(";")
        append("var __cv=").append(jsObject(context.collectionVariables)).append(";")

        // ── Runtime (constant) ────────────────────────────────────────────
        append(RUNTIME_JS)

        // ── Namespace + global aliases ────────────────────────────────────
        append("globalThis[").append(jsString(prefix)).append("]=__ns;")
        append(GLOBAL_ALIASES)

        // ── Execute user script in isolated inner IIFE ────────────────────
        append("try{(function(){")
        append(userScript)
        append("\n})();}catch(__e){__r.error=__e.message||String(__e);}")

        // ── Return results as JSON string ─────────────────────────────────
        append("return JSON.stringify(__r);")
        append("})()")
    }

    // ── JS helpers ────────────────────────────────────────────────────────

    private fun jsString(s: String?): String {
        if (s == null) return "null"
        return "\"${escapeJs(s)}\""
    }

    private fun jsObject(map: Map<String, String>): String {
        if (map.isEmpty()) return "{}"
        return map.entries.joinToString(",", "{", "}") { (k, v) ->
            "\"${escapeJs(k)}\":\"${escapeJs(v)}\""
        }
    }

    private fun escapeJs(s: String): String = buildString(s.length + 16) {
        s.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> {
                    if (ch.code in 0x00..0x1F) {
                        append("\\u")
                        append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
        }
    }.replace("</", "<\\/")

    private fun statusText(code: Int?): String = when (code) {
        null -> ""
        100 -> "Continue"
        101 -> "Switching Protocols"
        102 -> "Processing"
        103 -> "Early Hints"
        200 -> "OK"
        201 -> "Created"
        202 -> "Accepted"
        203 -> "Non-Authoritative Information"
        204 -> "No Content"
        205 -> "Reset Content"
        206 -> "Partial Content"
        207 -> "Multi-Status"
        208 -> "Already Reported"
        226 -> "IM Used"
        300 -> "Multiple Choices"
        301 -> "Moved Permanently"
        302 -> "Found"
        303 -> "See Other"
        304 -> "Not Modified"
        305 -> "Use Proxy"
        307 -> "Temporary Redirect"
        308 -> "Permanent Redirect"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        402 -> "Payment Required"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        406 -> "Not Acceptable"
        407 -> "Proxy Authentication Required"
        408 -> "Request Timeout"
        409 -> "Conflict"
        410 -> "Gone"
        411 -> "Length Required"
        412 -> "Precondition Failed"
        413 -> "Payload Too Large"
        414 -> "URI Too Long"
        415 -> "Unsupported Media Type"
        416 -> "Range Not Satisfiable"
        417 -> "Expectation Failed"
        418 -> "I'm a Teapot"
        421 -> "Misdirected Request"
        422 -> "Unprocessable Entity"
        423 -> "Locked"
        424 -> "Failed Dependency"
        425 -> "Too Early"
        426 -> "Upgrade Required"
        428 -> "Precondition Required"
        429 -> "Too Many Requests"
        431 -> "Request Header Fields Too Large"
        451 -> "Unavailable For Legal Reasons"
        500 -> "Internal Server Error"
        501 -> "Not Implemented"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        504 -> "Gateway Timeout"
        505 -> "HTTP Version Not Supported"
        506 -> "Variant Also Negotiates"
        507 -> "Insufficient Storage"
        508 -> "Loop Detected"
        510 -> "Not Extended"
        511 -> "Network Authentication Required"
        else -> ""
    }

    // ──────────────────────────────────────────────────────────────────────
    // Constant JavaScript runtime — defines:
    //   __r (results), response, request, environment, globals,
    //   collectionVariables, variables, __console, expect, test, __ns
    //
    // Context vars (__sc, __st, __rb, etc.) are injected above this block.
    // ──────────────────────────────────────────────────────────────────────
    private val RUNTIME_JS = """
var __r={tests:[],logs:[],envVars:{},globalVars:{},collVars:{},reqVars:{},mutations:{url:null,method:null,body:null,headers:{},queryParams:{}},error:null};
function __hl(h,n){var l=n.toLowerCase();for(var k in h){if(k.toLowerCase()===l)return h[k];}return undefined;}
var __rhO={};for(var __k in __rh){__rhO[__k.toLowerCase()]=__rh[__k];}__rhO.get=function(n){return __hl(__rh,n);};
var response={get code(){return __sc;},get status(){return __st;},get statusCode(){return __sc;},get responseTime(){return __rt;},get time(){return __rt;},get size(){return __rs;},text:function(){return __rb||'';},json:function(){return __rb?JSON.parse(__rb):null;},headers:__rhO,statusIs:function(c){if(__sc!==c)throw new Error('Expected status '+c+' but got '+__sc);}};
var __rqhO={};for(var __k2 in __rqh){__rqhO[__k2.toLowerCase()]=__rqh[__k2];}
__rqhO.get=function(n){return __r.mutations.headers[n]!==undefined?__r.mutations.headers[n]:__hl(__rqh,n);};
__rqhO.add=function(n,v){__r.mutations.headers[n]=String(v);};
__rqhO.upsert=function(n,v){__r.mutations.headers[n]=String(v);};
var __rqpO={};for(var __k3 in __rqp){__rqpO[__k3]=__rqp[__k3];}
__rqpO.get=function(n){return __r.mutations.queryParams[n]!==undefined?__r.mutations.queryParams[n]:__rqp[n];};
var request={get url(){return __r.mutations.url||__ru;},get method(){return __r.mutations.method||__rm;},get body(){return __r.mutations.body!==null?__r.mutations.body:(__rqb||'');},headers:__rqhO,query:__rqpO,params:__rqpO,setHeader:function(n,v){__r.mutations.headers[n]=String(v);},setQueryParam:function(n,v){__r.mutations.queryParams[n]=String(v);},setMethod:function(m){__r.mutations.method=m.toUpperCase();},setUrl:function(u){__r.mutations.url=u;},setBody:function(b){__r.mutations.body=b;}};
function __scope(init,bucket){return{get:function(k){return bucket[k]!==undefined?bucket[k]:init[k];},set:function(k,v){bucket[k]=String(v);},unset:function(k){delete bucket[k];delete init[k];},clear:function(){for(var k in bucket)delete bucket[k];for(var k in init)delete init[k];},has:function(k){return bucket[k]!==undefined||init[k]!==undefined;},toObject:function(){var o={};for(var k in init)o[k]=init[k];for(var k in bucket)o[k]=bucket[k];return o;}};}
var environment=__scope(__ev,__r.envVars);
var globals=__scope(__gv,__r.globalVars);
var collectionVariables=__scope(__cv,__r.collVars);
var variables={get:function(k){if(__r.reqVars[k]!==undefined)return __r.reqVars[k];if(__r.envVars[k]!==undefined)return __r.envVars[k];if(__ev[k]!==undefined)return __ev[k];if(__r.collVars[k]!==undefined)return __r.collVars[k];if(__cv[k]!==undefined)return __cv[k];if(__r.globalVars[k]!==undefined)return __r.globalVars[k];if(__gv[k]!==undefined)return __gv[k];return undefined;},set:function(k,v){__r.reqVars[k]=String(v);},unset:function(k){delete __r.reqVars[k];}};
var __console={log:function(){var a=[];for(var i=0;i<arguments.length;i++){var v=arguments[i];if(v===undefined){a.push('undefined');continue;}if(v===null){a.push('null');continue;}if(typeof v==='object'){try{a.push(JSON.stringify(v));}catch(_e){a.push('[Object]');}continue;}a.push(String(v));}__r.logs.push(a.join(' '));}};
function expect(actual){var neg=false;var chain={};function chk(pass,msg,negMsg){var p=neg?!pass:pass;if(!p)throw new Error(neg?(negMsg||'Negated assertion failed'):msg);}
['to','be','been','is','that','which','and','has','have','with','at','of','same','but','does','still','also','a','an'].forEach(function(p){Object.defineProperty(chain,p,{get:function(){return chain;},configurable:true});});
Object.defineProperty(chain,'not',{get:function(){neg=!neg;return chain;},configurable:true});
Object.defineProperty(chain,'exist',{get:function(){chk(actual!==null&&actual!==undefined,'expected value to exist but got '+JSON.stringify(actual),'expected value to not exist');return chain;},configurable:true});
Object.defineProperty(chain,'ok',{get:function(){chk(!!actual,'expected '+JSON.stringify(actual)+' to be truthy','expected value to not be truthy');return chain;},configurable:true});
Object.defineProperty(chain,'empty',{get:function(){var pass=actual===''||actual===null||actual===undefined||(Array.isArray(actual)&&actual.length===0)||(typeof actual==='object'&&actual!==null&&Object.keys(actual).length===0);chk(pass,'expected value to be empty','expected value to not be empty');return chain;},configurable:true});
Object.defineProperty(chain,'null',{get:function(){chk(actual===null,'expected null but got '+JSON.stringify(actual),'expected value to not be null');return chain;},configurable:true});
Object.defineProperty(chain,'true',{get:function(){chk(actual===true,'expected true but got '+JSON.stringify(actual),'expected value to not be true');return chain;},configurable:true});
Object.defineProperty(chain,'false',{get:function(){chk(actual===false,'expected false but got '+JSON.stringify(actual),'expected value to not be false');return chain;},configurable:true});
Object.defineProperty(chain,'undefined',{get:function(){chk(actual===undefined,'expected undefined but got '+JSON.stringify(actual),'expected value to not be undefined');return chain;},configurable:true});
chain.equal=chain.eql=chain.equals=function(expected){var pass=actual===expected;if(!pass)pass=JSON.stringify(actual)===JSON.stringify(expected);chk(pass,'expected '+JSON.stringify(expected)+' but got '+JSON.stringify(actual),'expected '+JSON.stringify(actual)+' to not equal '+JSON.stringify(expected));return chain;};
chain.include=chain.contain=chain.includes=chain.contains=function(val){var pass=false;if(typeof actual==='string')pass=actual.indexOf(val)!==-1;else if(Array.isArray(actual))pass=actual.indexOf(val)!==-1;else if(typeof actual==='object'&&actual!==null)pass=val in actual;chk(pass,'expected '+JSON.stringify(actual)+' to include '+JSON.stringify(val),'expected '+JSON.stringify(actual)+' to not include '+JSON.stringify(val));return chain;};
chain.match=function(re){var pass=false;if(re instanceof RegExp)pass=re.test(String(actual));else if(typeof re==='string')pass=new RegExp(re).test(String(actual));chk(pass,'expected '+JSON.stringify(actual)+' to match '+re,'expected '+JSON.stringify(actual)+' to not match '+re);return chain;};
chain.above=chain.greaterThan=function(n){chk(actual>n,'expected '+actual+' to be above '+n,'expected '+actual+' to not be above '+n);return chain;};
chain.below=chain.lessThan=function(n){chk(actual<n,'expected '+actual+' to be below '+n,'expected '+actual+' to not be below '+n);return chain;};
chain.least=function(n){chk(actual>=n,'expected '+actual+' to be at least '+n,'expected '+actual+' to not be at least '+n);return chain;};
chain.most=function(n){chk(actual<=n,'expected '+actual+' to be at most '+n,'expected '+actual+' to not be at most '+n);return chain;};
chain.oneOf=function(list){var pass=false;for(var i=0;i<list.length;i++){if(actual===list[i]||JSON.stringify(actual)===JSON.stringify(list[i])){pass=true;break;}}chk(pass,'expected '+JSON.stringify(actual)+' to be one of '+JSON.stringify(list),'expected '+JSON.stringify(actual)+' to not be one of '+JSON.stringify(list));return chain;};
chain.property=function(prop){chk(actual!==null&&actual!==undefined&&(prop in actual),'expected object to have property '+JSON.stringify(prop),'expected object to not have property '+JSON.stringify(prop));return chain;};
chain.lengthOf=chain.length=function(n){var len=actual?actual.length||0:0;chk(len===n,'expected length '+n+' but got '+len,'expected length to not be '+n);return chain;};
return chain;}
function test(name,fn){try{fn();__r.tests.push({name:name,passed:true,message:null});}catch(e){__r.tests.push({name:name,passed:false,message:e.message||String(e)});}}
var __ns={test:test,expect:expect,response:response,request:request,environment:environment,env:environment,globals:globals,global:globals,collectionVariables:collectionVariables,collection:collectionVariables,variables:variables,vars:variables,console:__console};
""".trimIndent()

    private val GLOBAL_ALIASES = """
globalThis.test=test;globalThis.expect=expect;globalThis.response=response;globalThis.request=request;
globalThis.console=__console;
globalThis.env=environment;globalThis.global=globals;globalThis.collection=collectionVariables;globalThis.vars=variables;
globalThis.environment=environment;globalThis.globals=globals;globalThis.collectionVariables=collectionVariables;globalThis.variables=variables;
// Legacy Postman sandbox globals — kept for scripts imported from older Postman collections.
var responseBody=__rb||'';
var responseCode={code:__sc,name:__st};
globalThis.responseBody=responseBody;globalThis.responseCode=responseCode;
""".trimIndent()
}
