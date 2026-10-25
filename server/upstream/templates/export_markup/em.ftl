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
<#--
/*	Optional variables:
*		1. noAddTestButtons - format String. Default value "false".
*		2. coverageFilePath - using Eclipse plugin need to press 'Update Coverage Source' button on report settings properties.
*		3. coverageStorageType - using console mode please set 'coverageFilePath' parameter as -coverageFilePath="path to coverage info file".
*/
-->	
<#include "../lib/ruconsts.ftl" /><#t>
<#include "../lib/comInfoTable.ftl" /><#t>
<#macro errordetect bool><#assign err=bool/></#macro><#t>
<#assign last = "0"/><#t>
<#assign col = 0/><#t>
<#assign req = -1/><#t>
<#assign lid = ""/><#t>
<#if !markupOnly??><#t>
<#assign markupOnly = "true"/><#t>
</#if><#t>
<#if pageRootReq.getType()=="Document"><#t>
	<#assign doc = pageRootReq/><#t>
	<#assign docModel = doc.getNodeModelWithFragments()/><#t>
	<?xml version="1.0" standalone="no"?>
</#if><#t>
<#macro colourDetect cl><#t>
	<#assign color="red"/><#t>
	<#if cl==1><#t>
		<#assign color="yellow"/><#t>
	<#else><#t>
		<#if cl==2><#t>
			<#assign color="green"/><#t>
		</#if><#t>
	</#if><#t>
	<#if cl==3><#t>
		<#assign color="gray"/><#t>
	</#if><#t>
</#macro>
<#macro colour idd cl><#t>
	<#if idd!=lid><#t>
		<#assign lid=idd/><#t>
		<#assign lcl=cl/><#t>
	<#else><#t>
		<#if cl==0 && lcl!=0><#t>
			<#assign lcl=1/><#t>
		<#else><#t>
			<#if cl==1 && lcl!=1><#t>
				<#assign lcl=1/><#t>
			<#else><#t>
				<#if cl==2 && lcl!=2><#t>
					<#assign lcl=1/><#t>
				<#else><#t>
					<#if cl==3 && lcl!=3><#t>
						<#assign lcl=1/><#t>
					</#if><#t>
				</#if><#t>
			</#if><#t>
		</#if><#t>
	</#if><#t>
	<@colourDetect lcl/><#assign lcolor=color/><#t>
</#macro> <#t>
<#macro showLinks req listed><#t>
	<#local testsCount = coverage.getCoverageTestCount(req.getQualifiedId())/><#t>
	<#local testPathList = coverage.getCoveragePaths(req.getQualifiedId())/><#t>
	<#local testPathLabels = coverage.getPathsLabels(req.getQualifiedId())/>
	<#local testCntList = coverage.getCoverageCounts(req.getQualifiedId())/><#t>
	<#if (testsCount>-1)><#local iterate = 0/><#t>
		<#list testPathList as path><#t>
			<#if !listed?seq_contains(path)><#t>
				<#assign pathname=testPathLabels[path]!path><#t>
				<#if (pathname?length>50)><#t>
					<#assign pathname=(pathname?substring(0,25)+"..."+pathname?substring(pathname?length-25,pathname?length))><#t>
				</#if><#t>
				<a title='${path?js_string}' onclick = 'if(typeof openEditor != "undefined"){if(openEditor("${path}")!=null)return false;}window.location.href="${path?js_string}";' href = "#">${pathname}  </a><br><#t>
				<#assign listed = listed + [ path ]/><#t>
			</#if><#t>
			<#if coverage.hasErrors("${req.getQualifiedId()}",path)><#t>
			<@errordetect 1/><#t>
				<#local errors_names=coverage.getErrorNamesForNodeAndTest(req.getQualifiedId(),path)/><#t>
				<#local errors_desc=coverage.getErrorDescriptionsForNodeAndTest(req.getQualifiedId(),path)/><#t>
				<#local errors_links=coverage.getErrorLinksForNodeAndTest(req.getQualifiedId(),path)/><#t>
					<ul><#list 0..errors_names?size-1 as i><#local error_name=(errors_names[i])><#local error_desc=errors_desc[i]><#local error_link=errors_links[i]><#t>
					<li title='${error_desc}'><#if error_link!=""><a style="color:black; text-decoration:none;"  onclick = 'if(typeof openEditor != "undefined"){if(openEditor("${error_link}")!=null)return false;}window.location.href="${error_link}";' href = "#"><span class="error">${error_name}</span></a><#else><span class="error">${error_name}</span></#if></li><#t>
				</#list></ul><#t>
			</#if></#list></#if><#t>
</#macro><#t>
 
<#macro recShown dc><#t>
	<#local childrenList = dc?children/><#t>
	<#if dc?node_name == "br"><#t>
		<br/><#t>
	<#else><#t>
		<#if dc?node_type == 'element'><#t>
			<#assign isImage=false/><#t>
			<#if dc?node_name=="img" || dc?node_name=="object"><#t>
				<#assign isImage=true/><#t>
			</#if><#t>
			<#assign lcolor = "gray"/><#t>
			<#assign addClass = ""/><#t>
			<#assign id = ""/><#t>
			<#if dc.@class?? && (dc.@class?size>0)><#t>
				<#list dc.@class as cl><#t>
					<#if cl?contains("location_")><#t>
						<#local id = cl?substring(cl?index_of("location_")+9)/><#t>
					</#if><#t>
				</#list><#t>
			</#if><#t>
			<#if dc?node_name=="span" && (id!="")><#t>
				<#assign req=-1/><#t>
				<#if uid?? && uid == (id)><#t>
					<#assign double=1/><#t>
				</#if><#t>
				<#assign uid = (id)><#t>
				<#assign node = utils.getNodeByUUID(uid)!""><#t>
				<#assign lcolor = "gray"/><#t>
				<#if node!=""><#t>
					<#list utils.getNodeByUUID(uid).getRequirements() as req2><#t>
						<@colour id 3/><#t>
						<#assign req=1/><#t>
					</#list><#t>
				<#else><#t>
					<#assign lcolor = "gray"/><#t>
					<#assign req=-1/><#t>
				</#if><#t>
				<#if req??><#t>
					<#if req==-1><#t>
						<#assign lcolor = "red"/><#t>
						<#assign addClass = " broken_loc"/><#t>
					<#else><#t>
						<@errordetect false/><#t>
						<#if err><#t>
							<#assign addClass = addClass+ " errors"/><#t>
						</#if><#t>
					</#if><#t>
					<#assign req=-1/><#t>
				</#if><#t>
				<#assign addClass=lcolor+addClass/><#t>
			</#if><#t>
			<#assign wasClass=false/><#t>
			<#list dc.@@ as attr><#t>
				<#if attr?node_name?lower_case=="class"><#assign wasClass=true/></#if><#t>
			</#list><#t>
			<${dc?node_name} <#list dc.@@ as attr><#assign val=attr/><#if isImage && (attr?node_name=="src"||attr?node_name=="data")><#assign val=docpath+val/></#if><#if attr?node_name?lower_case=="class">${attr?node_name}='${addClass} ${val?replace("'","\"")}' <#else>${attr?node_name}='${val?replace("'","\"")}' </#if></#list><#if !wasClass>class='${addClass}' </#if><#if dc?node_name?lower_case == "br"> /</#if>><#t>
			<#list childrenList as child><#t>
				<@recShown child /><#t>
			</#list><#t>
			<#if dc?node_name?lower_case != "br"><#t>
				</${dc.@@local_name}><#t>
			</#if><#t>
		<#else>${dc?html}</#if><#t>
	</#if><#t>
</#macro><#t>
<#macro showdocheadpart dc><#t>
	<#if dc?is_node><#t>
		<#local childrenList = dc?children/><#t>
		<#if (childrenList?size > 0)><#t>
			<#list childrenList as child><#t>
				<#if child?node_name?lower_case=="html"><#t>
					<#local childrenList2 = child?children/><#t>
					<#if (childrenList2?size > 0)><#t>
						<#list childrenList2 as child2><#t>
							<#if child2?node_name?lower_case=="head"><#t>
								<#list child2?children as child3><#t>
									<@recShown child3 /><#t>
								</#list><#t>
							</#if><#t>
						</#list><#t>
					</#if><#t>
				</#if><#t>
			</#list><#t>
		</#if><#t>
	</#if><#t>
</#macro><#t>
<#macro showdocbodypart dc><#t>
	<#if dc?is_node><#t>
		<#local childrenList = dc?children/><#t>
		<#if (childrenList?size > 0)><#t>
			<#list childrenList as child><#t>
				<#if child?node_name?lower_case=="html"><#t>
					<#local childrenList2 = child?children/><#t>
					<#if (childrenList2?size > 0)><#t>
						<#list childrenList2 as child2><#t>
							<#if child2?node_name?lower_case=="body"><#t>
								<#list child2?children as child3><#t>
									<@recShown child3 /><#t>
								</#list><#t>
							</#if><#t>
						</#list><#t>
					</#if><#t>
				</#if><#t>
			</#list><#t>
		</#if><#t>
	</#if><#t>
</#macro><#t>
<#macro showdoc dc path><#t>
	<#local childrenList = dc?children/><#t>
	<#if dc?node_name == "br"><#t>
		<br/><#t>
	<#else><#t>
		<#if dc?node_type == 'element'><#t>
			<#assign isImage=false/><#t>
			<#if dc?node_name=="img" || dc?node_name=="object"><#t>
				<#assign isImage=true/><#t>
			</#if><#t>
			<${dc?node_name} <#list dc.@@ as attr><#assign val=attr/><#if isImage && (attr?node_name=="src"||attr?node_name=="data")><#assign val=docpath+val/></#if>${attr?node_name}='${val?replace("'","\"")}' </#list>><#t>
		</#if><#t>
	</#if><#t>
	
	<#assign error=false/><#t>
	<#assign double=0/><#t>
	<#if dc?node_name == "span"><#t>
		<#assign id = ""/><#t>
		<#if dc.@class?? && (dc.@class?size>0)><#t>
			<#list dc.@class as cl><#t>
				<#if cl?contains("location_")><#t>
					<#local id = cl?substring(cl?index_of("location_")+9)/><#t>
				</#if><#t>
			</#list><#t>
		</#if><#t>
		<#if id!=""><#t>
			<#if uid?? && uid == id><#t>
				<#assign double=1/><#t>
			</#if><#t>
			<#assign uid = id><#t>
			<#assign node = utils.getNodeByUUID(uid)!""><#t>
			<#if node!=""><#t>
				<#list utils.getNodeByUUID(uid).getRequirements() as req><#t>
					<@colour id 3/><#t>
				</#list><#t>
			<#else><#t>
				<#assign class="orange"/><#t>
				<#assign req=-1/><#t>
			</#if><#t>
		</#if><#t>
	</#if><#t>
	<#list childrenList as child><#t>
		<@showdoc child path /><#t>
	</#list><#t>
	<#if dc?node_name=="span" && (!req?? || req==-1)><#t>
		<#assign req=0/><#t>
	</#if><#t>
	<#if dc?node_name != "br" && dc?node_type = 'element'><#t>
		{dc.@@end_tag}<#t>
	</#if><#t>
</#macro><#t>
<html><#t>
<head><#t>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"><#t>
<#if doc??><#t>
	<@showdocheadpart docModel/>
</#if><#t>
<style type="text/css"><#t>

<#if fitImages?? >
  img,object{
    max-width: 100%;
    object-fit: contain;
  }
</#if>
.fragment{
	background: #FFFFBB;
}
.fragment.even{
	background: rgb(177,177, 93);
}
.green {
	background: #bcee68;
}
.green.even{
	background: rgb(94,169,52);
}
.red {
	background: pink;
}
.red.even{
	background: rgb(177,96,101);
}
.yellow {
	background: #fff68f;
}
.yellow.even{
	background: rgb(177, 173, 71);
}

ul {
	list-style-type: none;
	margin-bottom: 0px;
	margin-top: 0px;
	margin-left:-30px;
}
a:link{
	color:#236b8e;
}

a:visited{
	color:#191970;
}
a:focus {
	color:#2f2f4f;
	text-shadow: 0px 0px 1px #2f2f4f, 1px 1px 2px #2f2f4f;
}
a:hover{
	text-shadow: 0px 0px 1px grey;
}
.error{	
	text-shadow: 0px 0px 1px red, 2px 2px 3px rgb(255, 182, 193);
}
.texterror{	
	border-bottom: 2px ridge red;
}
.selected-location{
	background: lightgray;
}
.selected-location.even{
	background: rgb(106, 106, 106);
}

.addtest{
	width: 100px;
	cursor: pointer; cursor: hand;
}
.font{
	vertical-align: 0px;
	text-transform: none;
	text-decoration: none;
	text-indent: 0px;
	color: black;
	word-spacing: 0px;
	letter-spacing: 0px;
	font-weight: normal;
	font-style: normal;
	font-variant: normal;
	font-family: Time New Roman, serif;
}
.context-menu {
    position: absolute; 
    padding:10px;
    border: 1px ridge silver;
    box-shadow: 5px 5px 10px rgba(122, 122, 122, 0.5);
	border-radius: 3px;
	background: #eee;
	background: -moz-linear-gradient(top,  rgba(255,255,255,1) 0%, rgba(246,246,246,1) 47%, rgba(237,237,237,1) 100%); /* FF3.6+ */
	background: linear-gradient(to bottom,  rgba(255,255,255,1) 0%,rgba(246,246,246,1) 47%,rgba(237,237,237,1) 100%); /* W3C */
}
.reportheader {
	border-radius: 5px 5px 0px 0px; 
	border: outset 1px grey; 
	width: 100%;
    padding: 5px 0 5px 0;
	background: rgb(204,204,204);
	background: -moz-linear-gradient(top,  rgba(204,204,204,1) 0%, rgba(238,238,238,1) 100%); /* FF3.6+ */
	background: linear-gradient(to bottom,  rgba(204,204,204,1) 0%,rgba(238,238,238,1) 100%); /* W3C */
}
.block {
	width:auto;
	//clear:both; 
	//padding:10px;	
	//background:rgba(184,186,198, 0.1); 
	//border: outset 1px silver;
	//border-top:none;
	//border-radius: 0px 0px 5px 5px;
	//box-shadow: 5px 5px 10px rgba(122, 122, 122, 0.5);
}
div.hidden{
	display: none;
	height:0px;
    overflow: hidden;
}
.broken_loc{
	background: silver;
}
body{
	min-width: 560px;
}	
#header {
	height: 70px;
	border-radius: 5px 5px 0 0;
	background: rgb(204,204,204); /* Old browsers */
	background: -moz-linear-gradient(top,  rgba(204,204,204,1) 0%, white 100%); /* FF3.6+ */
	background: linear-gradient(to bottom,  rgba(204,204,204,1) 0%, white 100%); /* W3C */
}
#header h1{
	font: bold 36px Arial serif;
	margin: 0px;
	padding:10px 10px 0 0;
	text-align: center;
	text-shadow: 1px 1px 0 #8E9FA5;
}	
#footer {
    height: 30px;
    text-align: right;
    padding:10px 10px 0 0;
    border-radius: 0 0 5px 5px;
	background: -moz-linear-gradient(top,  white 0%, rgba(219,220,226,1) 54%, rgba(184,186,198,1) 97%, rgba(184,186,198,1) 97%); /* FF3.6+ */
	background: linear-gradient(to bottom,  white 0%,rgba(219,220,226,1) 54%,rgba(184,186,198,1) 97%,rgba(184,186,198,1) 97%); /* W3C */
}
<#include "../lib/projecttabscss.ftl" /><#t>
<#include "../lib/comInfoCSS.ftl" /><#t>
</style><#t>
<script type="text/javascript"><#t>
	function testNode(node, cla){
		if(node == null || node.classList == null)
			return null;
		for(const lcls of node.classList)
        	if(lcls.indexOf(cla)>-1)
        		return node;
		return null;
    } 
	function recLook (node, cla){
        var res = testNode(node, cla);
		for(const chi of node.children){
			var tr = recLook(chi, cla);
			if(tr!=null)
				return tr;
		}
		return null;
	} 
	function recParent (node, cla){
		if(node == null)
			return null;
        var res = testNode(node, cla);
		if(res)
			return res;
		return recParent(node.parentNode, cla);
	}
	function docclick(e) {
 		evt = e ? e:window.event;
		var menus = document.getElementsByClassName("shown-menu");
		var spanTarg = null, targ = null;
		for(var i = 0;i<menus.length;i++){
			var menu = menus[i]; 
			menu.classList.add("hidden");
			menu.classList.remove("shown-menu");
			var uid = menu.id.substring(5);
			var locs = document.getElementsByClassName("location_"+uid);
			for(var j = 0;j<locs.length;j++){
				locs[j].classList.remove("selected-location");
			}
		}
		spanTarg = recParent (e.target, "fragment");
		if(spanTarg != null){
			id = spanTarg.className.substring(spanTarg.className.indexOf("location_")+9);
        	targ = document.getElementById("menu_"+id);
        }
		if(targ!=null){
			if (e.button == 2 || e.button == 0) {
			
				var locs = document.getElementsByClassName("location_"+id);
				for(var j = 0;j<locs.length;j++){
					locs[j].className="selected-location "+locs[j].className;
				}
				//spanTarg.className="selected-location "+spanTarg.className;
				var menu = targ;
				menu.classList.add("shown-menu");
				menu.classList.remove("hidden");
				menu.display = "";
				
 				var clickX=0, clickY=0;

				if ((evt.clientX || evt.clientY) &&
     			  document.body &&
     			  document.body.scrollLeft!=null) {
  					clickX = evt.clientX + document.body.scrollLeft;
  					clickY = evt.clientY + document.body.scrollTop;
 				}
 				if ((evt.clientX || evt.clientY) &&
     			  document.compatMode=='CSS1Compat' && 
     			  document.documentElement && 
     			  document.documentElement.scrollLeft!=null) {
  					clickX = evt.clientX + document.documentElement.scrollLeft;
  					clickY = evt.clientY + document.documentElement.scrollTop;
 				}
 				if (evt.pageX || evt.pageY) {
  					clickX = evt.pageX;
  					clickY = evt.pageY;
 				}
 				
				menu.style.position = "absolute";
				menu.style.top = clickY+"px";
				menu.style.left = clickX+"px";
				return false;
			}
		}
	};
	document.onclick = document.oncontextmenu = docclick;
</script><#t>
</head><#t>
<body><#t>
	<div id='menus'>
		<#if pageRootReq.getType()=="Document"><#t>
			<#list doc.getSortedShownChildren() as loc><#t>
				<#assign id = loc.getSUUId()/><#t>
				<#list loc.getRequirements() as req><#t>
					<div class="context-menu hidden" id="menu_${id}"><#t>
						<span id="font"><#t>
						<#list loc.getRequirements() as req><#t>
							<div style="margin-bottom:5px;" class="yellow"><#t>
							<#assign fufid=utils.getFullName(req)?js_string><#t>
							<#assign ufid=fufid><#t>
							<#if (ufid?length>60)><#t>
								<#assign ufid=("..."+ufid?substring(ufid?length-60,ufid?length))><#t>
							</#if><#t>
							<span title='${fufid}' style="font-weight:bold !important; color:black !important;"><#t>
							"${ufid}"<#t>
							</span><#t>
							<br><#t>
							<#assign listed=[]><#t>
							<#list req.attrs().getAllAttributeKeys() as key><#t>
								<#assign attr = req.attrs().getStringAttributeValue(key, "")/><#t>
								<#if key!="date" && attr!="" && req.attrs().getAttribute(key).isUserVisible()><#t>
									${key}: ${req.attrs().getStringAttributeValue(key)}<br/><#t>
								</#if><#t>
							</#list><#t>
							${req.getDescription(reportLocation)}<br/><#t>
							</div><#t>
						</#list><#t>
						</span><#t>
					</div><#t>
				</#list><#t>
			</#list><#t>
		</#if><#t>
	</div>
<#if pageRootReq.getType()!="Document">
	<div id="header"><h1>Requality Project</h1></div>
	
	<@showheader pageRootReq/>
	${utils.initMainGeneration()}<#t/>
			<#if noAddTestButtons?? && noAddTestButtons!="false">
				<#assign showButtons = true>
			<#else>
				<#assign showButtons = true>
			</#if>
			
			<#-- Формирование заголовка-->
			<div style="padding: 0 10px 10px 0;">
				<h2 style="margin-bottom:0px;"><table class="reportheader"><tr><th>Contents:</th></tr></table></h2>
				<div class="block"><ul>
					<#list utils.getDocuments() as doc>
						<#if (markupOnly?string!="true" || doc.haveMarkup())><#t>
						<li><a href="${utils.getTemplFileName(doc)}">${doc.getQualifiedId()}</a></li>
						</#if><#t>
					</#list>
				</ul></div>
			</div>		
	<div id="footer">${progress.getDateTime()}</div>
	<!-- ${.now?iso_local} -->
<#else>
	<div id="header"><h1>Requality Project</h1></div>
	<@showheader pageRootReq/>
	<#assign docpath="images/"+doc.getUUId()+"/">
	${doc.duplicateImages(reportLocation+"/"+docpath)}
	<h2 style="margin-bottom:0px;"><a id="${doc.getUUId()}"/><table class="reportheader"><tr><th>${doc.getQualifiedId()}</th></tr></table></h2>
	<@showdocbodypart docModel/>
	<br/>
	<!-- ${.now?iso_local} -->	
</#if>
</body>
</html>