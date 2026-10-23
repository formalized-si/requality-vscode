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
<#assign versInfo=utils.getMapVar("versioning")!{"PluginVersioning":"false","TreeVersioning":"false"}/>
<#assign versBool=((versInfo["PluginVersioning"]!"false")!="false")&&((versInfo["TreeVersioning"]!"false")!="false")/>
<#assign typeMap = { "примечание": "note", "обоснование": "just","описание": "def","":""}/>
<#include "../lib/ruconsts.ftl" />
<#include "../lib/tableinfo.ftl" />
<#include "../lib/projecttabs.ftl" />

<#macro showref req text islist tp val refines setname lvl>
	<#if lvl == 0>
		${utils.resetMap(setname)}
	</#if>
	<#if val?? && (islist || ReferenceValue.isInstance(val))>
		<#if (text??&&text?length>0)>
			${text}:
			<br/>
		</#if>
			<#if islist>
				<#if val.getValues??>
					<#list val.getValues() as node>
						<@showref req "" false val.getType() node refines setname lvl+1/>
					</#list>
				</#if>
			<#else>
				<#if val.getNodes?? && val.getNodes(req.attrs())??>
					<#local nodes = val.getNodes(req.attrs())/>
					<#list nodes as node>
						<#local fida = node.attrs().getStringAttributeValue("%SeqID")!""/>
						<#if fida!="">
							<#if !utils.isSameFile(node, req)><#t>
								${utils.addToMap(setname, utils.getLinkTextPrefix(node,req)+fida, utils.getTemplFileName(node)+"#"+fida)}
							<#else>
								${utils.addToMap(setname,fida, "#"+fida)}
							</#if>
						<#else>
							<#if !utils.isSameFile(node, req)><#t>
								${utils.addToMap(setname, utils.getLinkTextPrefix(node,req)+node.getUserVisibleName(), utils.getTemplFileName(node)+"#"+node.getUserVisibleName())}
							<#else>
								${utils.addToMap(setname,node.getUserVisibleName(), "#"+node.getUserVisibleName())}
							</#if>
						</#if>
					</#list>
				<#else>
					<#if val.getRaw??>
						${utils.addToMap(setname,refines.valueToString(req.attrs(),tp,val.getRaw()), "")}
					</#if>
				</#if>
			</#if>
	</#if>
	<#if lvl == 0>
		<div class = "tabind">
		<#local set = utils.getMap(setname)/>
		<#local first = true/>
		<#list set?keys as key>
			<#if first>
				<#local first = false/>
			<#else>
				<br/>
			</#if>
			<#local val = set[key]!"">
			<#if val != "">
				<a href = "${val}">${key}</a>
			<#else>
				<span class = "error">${key}</span>
			</#if>
		</#list>
		</div>
	</#if>
</#macro> 
<#macro showpath req path>
	<#global reqPath = path/>
	<#if (context.reportData.attrs().getBooleanAttributeValue("useProjectNumeration",false))>	<#global reqPath = req.attrs().getStringAttributeValue("_numericId")/></#if>
</#macro>
<#macro showheading req lvl path>
    <#if (coreUtils.isHeader(req))>
        <#local childrenList = req.getSortedShownChildren()/>
        <#if !_showRootData??>
        	<#assign _showRootData=true/>
        </#if>
    	<#local isRoot = req.getUUId() == pageRootReq.getUUId()/>
    	
        <#if (req.getUUId() != pageRootReq.getUUId() || _showRootData == true)>
    		<#local fid = "0"/>
    		<#if req.attrs().getStringAttributeValue("%SeqID")??>
    			<#local fid = req.attrs().getStringAttributeValue("%SeqID")/>
    		</#if>
    		<#local fidLink = fid/>
    		<#if fidLink?string == "0">
    			<#local fidLink = req.getSUUId()/>
    		</#if>
    		<@showpath req path/>
			<li class='toc-level${lvl}'><span class='toc-text'><a href='#${fidLink}'>${reqPath} ${req.getNameOrId()}</a></span><span class='toc-num'>${fid}</span></li>
			<#local i = 1/>
			<#if (path?length>0)>
				<#local path = path+"."/>
			</#if>
			<#list childrenList as child>
				<@showheading child lvl+1 path+i/>
				<#if coreUtils.isHeader(child)>
					<#local i = i + 1/>
				</#if>
			</#list>
		<#else>
			<#if (isRoot)><#local lvl = lvl-1/></#if><#t>
		    <#local i = 1/>
			<#list childrenList as child>
				<@showheading child lvl+1 path+i/>
				<#if coreUtils.isHeader(child)>
					<#local i = i + 1/>
				</#if>
			</#list>
		</#if>
    </#if>
</#macro>
<#macro showrev req nodes setname>
	${utils.resetMap(setname)}
	<#list nodes as node>
		<#local fida = "0"/>
		<#if node.attrs().getStringAttributeValue("%SeqID")??>
			<#local fida = node.attrs().getStringAttributeValue("%SeqID")/>
		<#else>
			<#local fida = node.getUserVisibleName()/>
		</#if>
			<#if !utils.isSameFile(node, req)><#t>
				${utils.addToMap(setname, utils.getLinkTextPrefix(node,req)+fida,utils.getTemplFileName(node)+"#"+fida)}
			<#else>
				${utils.addToMap(setname, fida,"#"+fida)}
			</#if>
	</#list>
	<#local set = utils.getMap(setname)/>
	<#local first = true/>
	<#list set?keys as key>
		<#if first>
			<#local first = false/>
		<#else>
			<br/>
		</#if>
		
		<a href = "${set[key]}">${key}</a>
	</#list>
</#macro>
<#macro showlink from to text><#t>
	<#local fida = "0"/><#t>
	<#if to.attrs().getStringAttributeValue("%SeqID")??><#t>
		<#local fida = to.attrs().getStringAttributeValue("%SeqID","N")/><#t>
		<#if utils.getTemplFileName(to)!=utils.getTemplFileName(from)><#t>
			<#local link=utils.getTemplFileName(to)+'#'+fida/><#t>
		<#else><#t>
			<#local link='#'+fida/><#t>
		</#if><#t>
	<#else><#t>
		<#if utils.getTemplFileName(to)!=utils.getTemplFileName(from)><#t>
			<#local link=utils.getTemplFileName(to)+'#'+fida/><#t>
		<#else><#t>
			<#local link='#'+fida/><#t>
		</#if><#t>
	</#if><#t>
	<a href = "${link}">${text}</a><#t>
</#macro><#t>
<#macro showreq req lvl path>
	<#if req.getType() == "Requirement" || req.getType() == "TextNode">
		<#if !_showRootData??>
        	<#assign _showRootData=true/>
        </#if>
	    <#if (req.getUUId() != pageRootReq.getUUId() || _showRootData == true)>
			<#local text = ""/>
			<#local isHeader = coreUtils.isHeader(req)/>
			<#local fid = "0"/>
			<#if req.attrs().getStringAttributeValue("%SeqID")??>
				<#local fid = req.attrs().getStringAttributeValue("%SeqID","N")/>
			</#if>
			<#local tp = "Требование"/>
			<#if coreUtils.isHeader(req)>
				<#local tp = "Заголовок"/>
			<#else>
				<#if req.attrs().getStringAttributeValue("Вид")??>
					<#local tp = req.attrs().getStringAttributeValue("Вид")/>
				</#if>
			</#if>
			<#local pr = ""/>
			<#if (req.attrs().getStringAttributeValue("Производное"))??>
				<#local pr = req.attrs().getStringAttributeValue("Производное")/>
			</#if>
			<#if isHeader>
				<@showpath req path/>
				<#local text = "<b>"+reqPath+" "+req.getNameOrId()+"</b>"/>
			<#else>
				<#if req.getDescription(reportLocation)?has_content>
					<#local cl = 'requirement'/>
					<#local kind=""/>
				    <#local kattr=req.attrs().getStringAttributeValue("Вид","")?lower_case/>
				    <#if !kattr?? || kattr?length==0>
				    	<#if req.getType()=="TextNode">
				    		<#local kattr="описание"/>
				    	</#if>
				    </#if>
					<#if kattr??&&typeMap[kattr]??>
						<#local kind = typeMap[kattr]/>
						<#if kind?? && (kind?length>0)>
							<#local cl = 'textnode-'+kind/>
						</#if>
					</#if>
		            <#local text=req.getDescription("<div class='"+cl+"'>","</div>",reportLocation,
		            										utils.getURIResolver())/>
				</#if>
			</#if>
		    <tr>
			<#list selAttrs as name>
				<#if name == "_description">
					<td class="col_${name_index}"><a name="${req.getUUId()}"></a><a name="${fid}"></a>${utils.refineValue(text,name,selFormula[name_index])}</td>
				<#else>
					<#if name == "%SeqID">
					<td class="col_${name_index}">${fid}<#-- Версия узла--><#if versBool><#local vers = req.getNodeVersion()/><#if vers!="NONE"><br/><span class="versInfo">v${vers}</span></#if></#if></td>
					<#else>
						<#if name == "Вид">
					<td class="col_${name_index}">${utils.refineValue(tp,name,selFormula[name_index])}</td>
						<#else>
							<#if name == "Производное">
					<td class="col_${name_index}">${utils.refineValue(pr,name,selFormula[name_index])}</td>
							<#else>
					<td class="col_${name_index}">${utils.refineValue(req.attrs().getStringAttributeValue(name, ""),name,selFormula[name_index])}</td>
							</#if>
						</#if>
					</#if>
				</#if>
			</#list>
			<td>
			<#if req.attrs().getAttribute("refines")??>
				<#local refines = req.attrs().getAttribute("refines")/>
				<@showref req "Уточняет" refines.isList() refines.getType() refines.getValue() refines "tablew" 0/>
			</#if>
			<#if utils.getRevertNodes(req, "refines")??>
				Уточняется в:<br>
				<div class = "tabind">
					<@showrev req utils.getRevertNodes(req, "refines"), "ref"/>
				</div>
			</#if>
			<#if req.attrs().getAttribute("rationale-ref")??>
				<#local refines = req.attrs().getAttribute("rationale-ref")/>
				<@showref req "Обоснование" refines.isList() refines.getType() refines.getValue() refines "tablew" 0/>
			</#if>
			<#if utils.getRevertNodes(req, "rationale-ref")??>
				Обосновывает:<br/>
				<div class = "tabind">
					<#local nodes = utils.getRevertNodes(req, "rationale-ref")/>
					<@showrev req nodes, "rat"/>
				</div>
			</#if>
			 <#local termsrevn = utils.getUseToDefTerms(req)![]/>
			 <#if (termsrevn?size>0)>
		 	См. определения:
				<div class = "tabind"><#t>
		 		<#local last = ""/><#t>
		 		<#local first = true/>
		 		<#list termsrevn as term><#t>
		 			<#local nodes = utils.getUseToDefTerms(req, term)![]/><#t>
		 			<#if (nodes?size>0)><#t>
			 			<#if (nodes?size>0)><#t>
			 				<#if (nodes?size==1)><#t>
			 					<#if first>
			 						<#local first = false/>
			 					<#else>
			 						<br/>
			 					</#if>
			 					<#local node = nodes?first/><#t>
								<#local fida = "0"/><#t>
								<@showlink req node term/>
			 				<#else>
				 				${term}: <#t>
				 				${utils.resetMap("revT")}<#t>
								<#list nodes as node><#t>
									<#local fida ="0"/><#t>
									<#if node.attrs().getStringAttributeValue("%SeqID")??><#t>
										<#local fida = node.attrs().getStringAttributeValue("%SeqID","N")/><#t>
										<#if !utils.isSameFile(node, req)><#t>
											${utils.addToMap("revT", utils.getLinkTextPrefix(node,req)+fida, utils.getTemplFileName(node)+'#'+fida)}<#t>
										<#else><#t>
											${utils.addToMap("revT", fida, "#"+fida)}<#t>
										</#if><#t>
									</#if><#t>
								</#list><#t>
								<#local set = utils.getMap("revT")/><#t>
								<#if (set?size>0)><#t>
									<br/><#t>
									<#list set?keys as key><#t>
										<#local val = set[key]!""/><#t>
											<#if val!=""><#t>
												<a href = "${val}">${key}</a><#t>
											</#if><#t>
										<#if set?keys?last != key>, </#if><#t>
									</#list><#t>
								</#if><#t>
							</#if><#t>
						</#if><#t>
					<#else>
						<div class = "notResolved">${term}</div>
					</#if><#t>
		 		</#list>
		 		</div>
		 	</#if>
			 <#local terms = utils.getDefToUseTerms(req)![]/>
			<#if (terms?size>0)>
			 	${utils.resetMap("terms")}
			 	<#local first = true/>
			 	<#if (terms?is_enumerable && terms?size>0)>
					 	<#list terms as term>
			 				<#local nodes = utils.getDefToUseNodes(req, term)/>
				 			<#list nodes as node>
				 				<#if (node.attrs().getStringAttributeValue("%SeqID")??)>
									<#local fida = node.attrs().getStringAttributeValue("%SeqID")/>
									<#if (fida??&&fida?length>0)>
										<#if !utils.isSameFile(node, req)><#t>
											${utils.addToMapList("terms", term, utils.getTemplFileName(node)+"#"+fida)}
											${utils.addToMapList("terms", term, utils.getLinkTextPrefix(node,req)+fida)}
										<#else>
											${utils.addToMapList("terms", term, "#"+fida)}
											${utils.addToMapList("terms", term, fida)}
										</#if>
									</#if>
								<#else>
									<#if !utils.isSameFile(node, req)><#t>
										${utils.addToMapList("terms", term, utils.getTemplFileName(node)+"#"+node.getUUId())}
										${utils.addToMapList("terms", term, utils.getLinkTextPrefix(node,req)+node.getUserVisibleName())}
									<#else>
										${utils.addToMapList("terms", term, "#"+node.getUUId())}
										${utils.addToMapList("terms", term, node.getUserVisibleName())}
									</#if>
								</#if>
				 			</#list>
					 	</#list>
					 	<#local set = utils.getMapList("terms")>
					 	<#if (set?keys?size==1)>
					 	Использование данного определения:
					 	<#else>
					 		<#if (set?keys?size>1)>
					 	Использования введённых определений:
					 		</#if>
					 	</#if>
			 		<div class = "tabind">
					 	<#list set?keys as key>
					 		<#local seq = set[key]/>
					 		<#if (set?keys?size>1)>
						 		<#if first>
			 						<#local first = false/>
			 					<#else>
			 						<br/>
			 					</#if>
					 		${key}:<br/> 
					 		</#if>
				 			<#local b = true/>
				 			<#list seq as node>
				 				<#if b>
				 					<a href = "${node}">
				 				<#else>
				 					${node}</a><#if seq?last != node>, </#if>
				 				</#if>
				 				<#if b><#local b = false/><#else><#local b = true/></#if>
				 			</#list>
					 	</#list>
					 </div>
				 </#if>
			<br/>
			</#if>
			
			<#if utils.getRevertNodes(req, "next-ref")??>
				Ранее:<br>
				<div class = "tabind">
				<#local nodes = utils.getRevertNodes(req, "next-ref")/>
				<@showrev req nodes "bef"/>
				</div>
			</#if>
			<#if req.attrs().getAttribute("next-ref")??>
				<#local refines = req.attrs().getAttribute("next-ref")/>
				<@showref req "Далее" refines.isList()  refines.getType() refines.getValue() refines "tablew" 0/>
			</#if>
			</td>
			</tr>
		    <#if isHeader>
		        <#local childrenList = req.getSortedShownChildren()/>
				<#if (path?length>0)>
					<#local path = path+"."/>
				</#if>
				<#local i = 1/>
				<#list childrenList as child>
					<@showreq child lvl+1 path+i/>
					<#if coreUtils.isHeader(child)>
						<#local i = i + 1/>
					</#if>
				</#list>
			</#if>
		<#else>
			<#local isHeader = coreUtils.isHeader(req)/>
			<#if isHeader>
		        <#local childrenList = req.getSortedShownChildren()/>
				<#if (path?length>0)>
					<#local path = path+"."/>
				</#if>
				<#local i = 1/>
				<#list childrenList as child>
					<@showreq child lvl+1 path+i/>
					<#if coreUtils.isHeader(child)>
						<#local i = i + 1/>
					</#if>
				</#list>
			</#if>
		</#if>
	</#if>
</#macro>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
<head>
  <meta http-equiv="content-type" content="text/html; charset=utf-8"/>
  <title></title>
  <style type="text/css">
  .tabind{
    padding-left: 25px;
  }
  .versInfo{
    color:#505050;
    font-weight: bold;
    font-size:-1em;
    
  }
    p { margin: 1mm }
    h3 { font-family: sans-serif }
    table, th, td { border: 1px solid black; }
    table { border-collapse: collapse; margin-top: 5mm }
    .meta          { background: #fafafa }
    .attribute     { background: #eff6ff }
    .textnode      { background: #ccffcc }
    .textnode-def  { background: #ccffcc }
    .textnode-note { background: #c9ffff }
    .textnode-just { background: #daebdc }
    .requirement   { background: #ffefff }
    .error         { background: #ff0000 }

    /* Table of contents */
    ul.toc {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .toc a {
      text-decoration: none;
    }
    .toc-level0 span.toc-text {
      font-weight: bold;
      text-decoration: underline;
      font-size: 120%
    }
    .toc-level1 span.toc-text {
      font-weight: bold;
      font-size: 110%
    }
    .toc-level2 span.toc-text {
      padding-left:4mm;
      font-size: 100%
    }
    .toc-level3 span.toc-text {
      padding-left:8mm;
      font-size: 95%
    }
    .toc-level4 span.toc-text {
      padding-left:12mm;
      font-size: 90%
    }
    .toc-level5 span.toc-text {
      padding-left:16mm;
      font-size: 88%
    }
    .toc-level6 span.toc-text {
      padding-left:20mm;
      font-size: 86%
    }
    .toc-level7 span.toc-text {
      padding-left:24mm;
      font-size: 84%
    }
    .toc-level8 span.toc-text {
      padding-left:28mm;
      font-size: 82%
    }
    .toc-level9 span.toc-text {
      padding-left:32mm;
      font-size: 80%
    }
    .toc-level10 span.toc-text {
      padding-left:36mm;
      font-size: 78%
    }
    .toc-level11 span.toc-text {
      padding-left:40mm;
      font-size: 76%
    }
    .toc-level12 span.toc-text {
      padding-left:44mm;
      font-size: 74%
    }
    .toc-level13 span.toc-text {
      padding-left:48mm;
      font-size: 72%
    }
    .toc-level14 span.toc-text {
      padding-left:52mm;
      font-size: 70%
    }
    .toc-num {
      float:right;
      padding-left:5px;
      visibility: hidden;
    }

    /* Popup container */
    .popup {
      position: relative;
      display: inline-block;
      cursor: pointer;
    }

    /* The actual popup (appears on top) */
    .popup .popuptext {
      visibility: hidden;
      max-width: 700px;
      min-width: 400px;
      border-radius: 5px;
      position: absolute;
      z-index: 1;
      bottom: 125%;
      right: 100%;
      margin-right: -50px;
      background-color: #e0e0e0;
      border-color: #e0e0e0;
      border: 1px dotted black;
    }

    /* Popup arrow */
    .popup .popuptext::after {
      content: "";
      position: absolute;
      top: 100%;
      right: 100%;
      /*margin-left: -2px;*/
    }

    /* Toggle this class when clicking on the popup container (hide and show the popup) */
    .popup .show {
      visibility: visible;
      animation: fadeIn 1s
    }

    /* Add animation (fade in the popup) */
    @keyframes fadeIn {
      from {opacity: 0;}
      to {opacity:1 ;}
    }
    <#include "../lib/tablecss.ftl" />
    <#include "../lib/projecttabscss.ftl" />
  <#if !selAttrs??>
	<#assign selAttrs=["%SeqID", "_description", "Вид", "Производное"]/>
  <#else>
	<#assign selAttrs=selAttrs.getValues()/>
  </#if>
  <#if !selAttrsWidth??>
	<#assign selAttrsWidth=[]/>
  <#else>
	<#assign selAttrsWidth=selAttrsWidth.getValues()/>
  </#if>
  <#if !selFormula??>
	<#assign selFormula=[]/>
  <#else>
	<#assign selFormula=selFormula.getValues()/>
  </#if>
  <#assign selAttrsNames=utils.getAttrVis(pageRootReq.getTreeDB(),selAttrs)/>
  <#assign attrWidths=utils.getAttrWidth(selAttrs,selAttrsWidth)/>
  <#assign selFormula=utils.getAttrFormula(selAttrs, selFormula)/>
  <#list selAttrsNames as name>
	<#assign params = "">
		.col_${name_index}{
	<#if (attrWidths[name_index]?length>0)>
		  max-width:${attrWidths[name_index]};
		  width:${attrWidths[name_index]};
	</#if>
		}
  </#list>
  </style>
  <script>
    function popupdef(id) {
      var popup = document.getElementById(id);
      popup.classList.toggle("show");
    }
  </script>
</head>
<body lang="ru-RU" dir="ltr">
<@showTabs pageRootReq/>
<@showheader pageRootReq/>
${utils.initMainGeneration()}<#t/>
<h3>Содержание</h3>
<div>
<ul class='toc'>
<@showheading pageRootReq 0 ""/>
</ul>
</div>
<hr>
<table><tbody><tr class="theader">
<#list selAttrsNames as name>
	<th class="col_${name_index}">${name}</th>
</#list>
<th>Ссылки</th>
</tr>
<@showreq pageRootReq 0 ""/>
</tbody></table>
<!-- ${.now?iso_local} -->
</body>
</html>