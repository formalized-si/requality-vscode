<#--
/* Copyright 2009-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 -->
 <#assign firstPage=false/>
<#include "../lib/ruconsts.ftl" />
<#include "../lib/tableinfo.ftl" />
<#include "../lib/projecttabs.ftl" />
 <html>
 <head>
 	<meta charset="UTF-8">
 	<style>
 	.hidden{
	    height:0px;
	    overflow: hidden;
	}
    <#include "../lib/projecttabscss.ftl" />
    <#include "../lib/tablecss.ftl" />
 	</style>
 	
	<script>
		const png1 = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABQAAAAUCAQAAAAngNWGAAAAAmJLR0QAAKqNIzIAAAAJcEhZcwAAAEgAAABIAEbJaz4AAABoSURBVCjP1c/NCkBAGIXhN1nasUXI+Mv1z9BEI8Ud2dpaqPmWnOXp6dSB/yUhk8GJhVECFROGRkJzdhy9hHasaBlNcTjqZxW8woiLgNC317AxU/jYgOVA+ViLxVD5b2hOCYOYUsK+mRuFlREpEzrazAAAACV0RVh0ZGF0ZTpjcmVhdGUAMjAxNS0wOS0wOFQwNzowNTozOS0wNDowMEXWUfsAAAAldEVYdGRhdGU6bW9kaWZ5ADIwMTUtMDktMDhUMDc6MDU6MzktMDQ6MDA0i+lHAAAAAElFTkSuQmCC',
		png2="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABQAAAAUCAYAAACNiR0NAAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH3wEMCzYiCCOtIgAAAIlJREFUOMvt080KAWEUxvEfZcFGYitpMgy5OpfCvaCUDOUOXIqlLLwLpslHZvn+l+c5nfM8nQ6RSCV0scbkTU+KLXpFoV7S3EYHK8xK9AGWaKD1rcsEG+wwfqpnOAVt/mv0BGfkYVAfexwKS16ofRiahug3NHHFApd/jjTEMTibVnX5DKP4AJEHd6dzEM+IXu5IAAAAAElFTkSuQmCC";
		function get_png_image_src(ind){
			if(ind == 0)
	 			return png2;
 			return png1; 
		}

		function toggle(id, i)
		{	
			var g = document.getElementById(i);
			if(g.src==png1)
				g.src = png2;
			else
				g.src = png1;
		    var e = document.getElementById(id);
		    if (e.className == "hidden"){
		      	e.className = "block";
		    }else{
		      	e.className = "hidden";
		    }
		    return true;
		}		
		function expandAll()
		{
			var e = document.getElementsByClassName("hidden");
			while (e.length>0){
				var id = e[0].id.substring(6);
				document.getElementById("img-"+id).src=png2;
				e[0].className="block";
			}
			return true;	
		}
		function retractAll()
		{
			var e = document.getElementsByClassName("block");
			while (e.length>0){
				var id = e[0].id.substring(6);
				document.getElementById("img-"+id).src=png1;
				e[0].className="hidden";
			}
			return true;	
		}
	</script>
 </head>
 <body>
<#macro showreq req>
    <#local node = req/>
    <#local columns = trace.getColumnNames()/>
    <#list columns as rname>
    	<#if !rname??>
    		<#local rname = "undef"/>
    	</#if>
    	<#if (trace.hasRevertTraces(rname))>
    		<#if toggleEnabled>
    			<div title="${rname}" onclick="toggle('block-${rname}','img-${rname}')" >
    				<img id="img-${rname}" src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABQAAAAUCAQAAAAngNWGAAAAAmJLR0QAAKqNIzIAAAAJcEhZcwAAAEgAAABIAEbJaz4AAABoSURBVCjP1c/NCkBAGIXhN1nasUXI+Mv1z9BEI8Ud2dpaqPmWnOXp6dSB/yUhk8GJhVECFROGRkJzdhy9hHasaBlNcTjqZxW8woiLgNC317AxU/jYgOVA+ViLxVD5b2hOCYOYUsK+mRuFlREpEzrazAAAACV0RVh0ZGF0ZTpjcmVhdGUAMjAxNS0wOS0wOFQwNzowNTozOS0wNDowMEXWUfsAAAAldEVYdGRhdGU6bW9kaWZ5ADIwMTUtMDktMDhUMDc6MDU6MzktMDQ6MDA0i+lHAAAAAElFTkSuQmCC"/>
    		<#else>
    			<div title="${rname}">
    		</#if>
    		${trace.formFirstString(firstPage, rname, pageRootReq, filter)}
   			</div>
   			<#if toggleEnabled>
    			<div id="block-${rname}" class="hidden">
    		<#else>
    			<div id="block-${rname}" class="block">
    		</#if>
	    		<#list trace.getRevertTraces(rname) as name>
	    			${trace.formSecondString(firstPage, name.getKey(), name.getValue(), pageRootReq, filter)}
    			</#list>
    		</div>
    	</#if>
    </#list>
</#macro>
<#if !filter??>
	<#assign filter = "*"/>
</#if>
	<#if !what??>
		<#assign what = "reference"/>
	</#if>
	<#if !toggleEnabled??>
    	<#assign toggleEnabled = true/>
    <#else>
    	<#if toggleEnabled?string=="true">
    		<#assign toggleEnabled = true/>
    	<#else>
    		<#assign toggleEnabled = false/>
    	</#if>
    </#if>
	
	<#if toggleEnabled>
		<table width = "100%" border = "0">
		<tr><td>
	</#if>
	<a href="${utils.getTemplFileName(pageRootReq,'trace.ftl')}">[${trace.getRevertName(firstPage,pageRootReq,filter)}]</a> [${trace.getDirectName(firstPage, rootReq,filter)}]
	<#if toggleEnabled>
		</td><td align = right>
		<a href="#" onclick="expandAll()">[Expand all]</a> <a href="#" onclick="retractAll()">[Retract all]</a>
		</td></tr></table>
	<#else>
		<br>
	</#if><hr>
<@showTabs pageRootReq/>
<@showheader pageRootReq/>
<hr/>
	${trace.initData(utils, pageRootReq,type1,type2,what,filter,traceabilitySettings,userData)}
	<@showreq pageRootReq/>
	<#if root.getError() != "">
   		<errors><![CDATA[${utils.htmlEscape(root.getError())}]]></errors>
	</#if>
</body>
</html>
