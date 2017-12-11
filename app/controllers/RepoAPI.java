/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the 
 * terms of the Apache License, Version 2.0 which is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0 
 ********************************************************************************/
package controllers;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import models.Module;
import models.Module.QueryParams;
import models.Module.QueryParams.Retrieval;
import models.Module.QueryParams.Suffix;
import models.ModuleVersion;
import play.Logger;
import play.mvc.Before;
import util.ApiVersion;

public class RepoAPI extends MyController {
    
    public static final int RESULT_LIMIT = 20;

    @Before
    public static void fixFormat(){
        Logger.info(request.format);
        // default to json
        if(request.format == null
                || (!request.format.equals("json")
                        && !request.format.equals("xml")))
            request.format = "json";
        // Play doesn't set the charset for us when rendering a template :(
        response.contentType = "application/"+request.format+"; charset="+response.encoding;
    }
    
    public static void completeVersions(String apiVersion, String module, String version, String type,
            // < V5
            Integer binaryMajor, Integer binaryMinor,
            // >= V5
            Integer jvmBinaryMajor, Integer jvmBinaryMinor,
            Integer jsBinaryMajor, Integer jsBinaryMinor,
            // >= V4
            String memberName, Boolean memberSearchPackageOnly, Boolean memberSearchExact, String retrieval){
        if(module == null || module.isEmpty())
            badRequest("module parameter required");
        Module mod = Module.findByName(module);
        if(mod == null)
            notFound("Module not found");
        ApiVersion v = getApiVersion(apiVersion);
        QueryParams t = getQueryParams(v, type, retrieval, binaryMajor, binaryMinor,
                jvmBinaryMajor, jvmBinaryMinor,
                jsBinaryMajor, jsBinaryMinor,
                memberName, memberSearchPackageOnly, memberSearchExact);
        
        List<ModuleVersion> versions = ModuleVersion.completeVersionForModuleAndBackend(mod, version, t);
        
        renderArgs.put("apiVersion", v);
        render(versions);
    }

    private static QueryParams getQueryParams(ApiVersion v, String type,
            // V4
            String retrieval,
            // < V5
            Integer binaryMajor, Integer binaryMinor,
            // >= V5
            Integer jvmBinaryMajor, Integer jvmBinaryMinor,
            Integer jsBinaryMajor, Integer jsBinaryMinor,
            // V4
            String memberName, Boolean memberSearchPackageOnly, Boolean memberSearchExact) {
        if (v.ordinal() >= ApiVersion.API4.ordinal()) {
            QueryParams params;
            if(v.ordinal() >= ApiVersion.API5.ordinal())
                params = getQueryParamsV4(type, retrieval, jvmBinaryMajor, jvmBinaryMinor, jsBinaryMajor, jsBinaryMinor);
            else
                params = getQueryParamsV4(type, retrieval, binaryMajor, binaryMinor, binaryMajor, binaryMinor);
            params.memberName = memberName;
            params.memberSearchExact = (memberSearchExact != null) ? memberSearchExact : false;
            params.memberSearchPackageOnly = (memberSearchPackageOnly != null) ? memberSearchPackageOnly : false;
            return params;
        } else {
            return getQueryParamsV3(type, binaryMajor, binaryMinor);
        }
    }

    private static QueryParams getQueryParamsV4(String type, String retrieval, 
            Integer jvmBinaryMajor, Integer jvmBinaryMinor,
            Integer jsBinaryMajor, Integer jsBinaryMinor) {
        if(type == null || type.isEmpty()){
            QueryParams qp = QueryParams.JVM();
            qp.jvmBinaryMajor = jvmBinaryMajor;
            qp.jvmBinaryMinor = jvmBinaryMinor;
            qp.jsBinaryMajor = jsBinaryMajor;
            qp.jsBinaryMinor = jsBinaryMinor;
            return qp;
        }
        String[] artifacts = type.split(",");
        ArrayList<Suffix> suffixes = new ArrayList<Suffix>(artifacts.length);
        for (String art : artifacts) {
            if(art.equalsIgnoreCase(".car"))
                suffixes.add(Suffix.CAR);
            else if(art.equalsIgnoreCase(".jar"))
                suffixes.add(Suffix.JAR);
            else if(art.equalsIgnoreCase(".js"))
                suffixes.add(Suffix.JS);
            else if(art.equalsIgnoreCase("module-resources"))
                suffixes.add(Suffix.RESOURCES);
            else if(art.equalsIgnoreCase(".src"))
                suffixes.add(Suffix.SRC);
            else if(art.equalsIgnoreCase("module-doc"))
                suffixes.add(Suffix.DOCS);
            else if(art.equalsIgnoreCase(".scripts.zip"))
                suffixes.add(Suffix.SCRIPTS_ZIPPED);
        }
        if (suffixes.isEmpty()) {
            error(HttpURLConnection.HTTP_BAD_REQUEST, "Unknown or no suffixes passed");
        }
        Suffix[] sufs = new Suffix[suffixes.size()];
        Retrieval ret = Retrieval.ANY;
        if (retrieval != null && !retrieval.isEmpty()) {
            if ("any".equalsIgnoreCase(retrieval)) {
                ret = Retrieval.ANY;
            } else if ("all".equalsIgnoreCase(retrieval)) {
                ret = Retrieval.ALL;
            } else {
                error(HttpURLConnection.HTTP_BAD_REQUEST, "Unknown retrieval, must be 'any' or 'all'");
            }
        }
        QueryParams qp = new QueryParams(ret, suffixes.toArray(sufs));
        qp.jvmBinaryMajor = jvmBinaryMajor;
        qp.jvmBinaryMinor = jvmBinaryMinor;
        qp.jsBinaryMajor = jsBinaryMajor;
        qp.jsBinaryMinor = jsBinaryMinor;
        return qp;
    }

    private static QueryParams getQueryParamsV3(String type, Integer binaryMajor, Integer binaryMinor) {
        QueryParams qp;
        if(type == null || type.isEmpty())
            qp = QueryParams.JVM();
        else if(type.equalsIgnoreCase("car"))
            qp = QueryParams.CAR();
        else if(type.equalsIgnoreCase("jar"))
            qp = QueryParams.JAR();
        else if(type.equalsIgnoreCase("jvm"))
            qp = QueryParams.JVM();
        else if(type.equalsIgnoreCase("javascript"))
            qp = QueryParams.JS();
        else if(type.equalsIgnoreCase("source"))
            qp = QueryParams.SRC();
        else if(type.equalsIgnoreCase("all"))
            qp = QueryParams.ALL();
        else if(type.equalsIgnoreCase("code"))
            qp =  QueryParams.CODE();
        else {
            error(HttpURLConnection.HTTP_BAD_REQUEST, "Unknown type, must be one of: car,jvm,javascript,source,all,code,ceylon");
            return null; // We'll never get here
        }
        qp.jvmBinaryMajor = binaryMajor;
        qp.jvmBinaryMinor = binaryMinor;
        qp.jsBinaryMajor = binaryMajor;
        qp.jsBinaryMinor = binaryMinor;
        return qp;
    }

    public static void completeModules(String apiVersion, String module, String type, 
            // < V5
            Integer binaryMajor, Integer binaryMinor,
            // >= V5
            Integer jvmBinaryMajor, Integer jvmBinaryMinor,
            Integer jsBinaryMajor, Integer jsBinaryMinor,
            // >= V4
            String memberName, Boolean memberSearchPackageOnly, Boolean memberSearchExact, String retrieval){
        Integer start = 0;
        ApiVersion v = getApiVersion(apiVersion);
        QueryParams t = getQueryParams(v, type, retrieval, 
                binaryMajor, binaryMinor, 
                jvmBinaryMajor, jvmBinaryMinor, jsBinaryMajor, jsBinaryMinor,
                memberName, memberSearchPackageOnly, memberSearchExact);

        List<Module> modules = Module.completeForBackend(module, t);
        long total = Module.completeForBackendCount(module, t);
        
        renderModulesTemplate(v, t, modules, start, total);
    }

    public static void searchModules(String apiVersion, String query, String type, Integer start, Integer count, 
            // < V5
            Integer binaryMajor, Integer binaryMinor,
            // >= V5
            Integer jvmBinaryMajor, Integer jvmBinaryMinor,
            Integer jsBinaryMajor, Integer jsBinaryMinor,
            // >= V4
            String memberName, Boolean memberSearchPackageOnly, Boolean memberSearchExact, String retrieval) {
        start = checkStartParam(start);
        count = checkCountParam(count);
        ApiVersion v = getApiVersion(apiVersion);
        QueryParams t = getQueryParams(v, type, retrieval, 
                binaryMajor, binaryMinor,
                jvmBinaryMajor, jvmBinaryMinor, jsBinaryMajor, jsBinaryMinor,
                memberName, memberSearchPackageOnly, memberSearchExact);
        
        List<Module> modules = Module.searchForBackend(v, query, t, start, count);
        long total = Module.searchForBackendCount(v, query, t);
        
        renderModulesTemplate(v, t, modules, start, total);
    }

    private static void renderModulesTemplate(ApiVersion v, QueryParams params, List<Module> modules, Integer start, long total) {
        // we need to put those in renderArgs rather than render() because they may be null
        renderArgs.put("type", params);
        renderArgs.put("apiVersion", v);
        renderArgs.put("jvmBinaryMajor", params.jvmBinaryMajor);
        renderArgs.put("jvmBinaryMinor", params.jvmBinaryMinor);
        renderArgs.put("jsBinaryMajor", params.jsBinaryMajor);
        renderArgs.put("jsBinaryMinor", params.jsBinaryMinor);
        renderTemplate("RepoAPI/modules." + request.format, modules, start, total);
    }

    private static Integer checkStartParam(Integer start) {
        if (start == null || start < 0)
            start = 0;
        return start;
    }

    private static Integer checkCountParam(Integer count) {
        if (count == null || count < 0 || count > RESULT_LIMIT)
            count = RESULT_LIMIT;
        return count;
    }
    
    private static ApiVersion getApiVersion(String apiVersion) {
        for(ApiVersion v : ApiVersion.values()){
            if(v.version.equals(apiVersion))
                return v;
        }
        badRequest("Invalid apiVersion parameter: "+apiVersion+". This instance of Herd supports 1 or 2.");
        return null;
    }
    
}
