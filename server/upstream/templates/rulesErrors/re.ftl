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
<#assign typeMap = { "примечание": "note", "обоснование": "just","описание": "def","":""}/>
<#include "../lib/ruconsts.ftl" />
<#include "../lib/tableinfo.ftl" />
<#include "../lib/projecttabs.ftl"/>

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
<#macro showheading req lvl path propedProblems relatedNodes>
    <#if relatedNodes?seq_contains(req.getSUUId())>
    	<#local pp = propedProblems[req.getUUId()]![]/>
        <#local childrenList = req.getSortedShownChildren()/>
    	<#if (lvl>0)>
    		<#local fid = "0"/>
    		<#local fidvisible = ""/>
    		<#if req.attrs().getStringAttributeValue("%SeqID")??>
    			<#local fid = req.attrs().getStringAttributeValue("%SeqID")/>
    			<#local fidvisible = fid/>
    		</#if>
    		<#if (pp?size>0)>
    			<#local fnode = req/>
    			<#local fid = coreUtils.getFIDOrUUID(context, fnode, false)!0/>
    		</#if>
    		<@showpath req path/>
    		
    		<#if reqPath?ends_with(".0")>
    			<#local reqPath = path?remove_ending(".0")+".X"/>
    		</#if>
    		
    		<#local fidLink = fid/>
    		<#if fidLink?string == "0">
    			<#local fidLink = req.getSUUId()/>
    		</#if>
			<li class='toc-level${lvl}<#if (pp?size>0)> toc-prob</#if>'><span class='toc-text'><a href='#${fidLink}'>${reqPath} ${req.getNameOrId()} <#if (fidvisible?length>0)>(${fidvisible})</#if></a></span><span class='toc-num'>${fid}</span></li>
		</#if>
		<#local i = 0/>
		<#local fi = 0/>
		<#local si = 0/>
		<#if !_showRootData??>
        	<#assign _showRootData=true/>
        </#if>
    	<#local isRoot = (req.getUUId() == pageRootReq.getUUId() && !_showRootData)/>
		<#if (isRoot)><#local lvl = lvl-1/></#if><#t>
		<#if (path?length>0)>
			<#local path = path+"."/>
		</#if>
		<#list childrenList as child>
			<#local fi = fi + 1/>
			<#if coreUtils.isHeader(child)>
				<#local i = i + 1/>
				<#local si = i/>
			<#else>
				<#local si = fi/>
			</#if>
			<@showheading child lvl+1 path+si propedProblems relatedNodes/>
		</#list>
    </#if>
</#macro>
<#macro showProblems node errors>
	<#local attributesError = false/>
	<#list errors as errorCode, errorList>
		<#switch errorCode>
			<#case "problem_rule_error">
				<p>При выполнении правил оформления узлов произошла ошибка:
        		<#local first = true/>
				<#list errorList as error>
					<#if first><#local first = false/><#else>, </#if></p><p>Правило '${error.getAdditionalData()[0]}' привело к ошибке: '${error.getAdditionalData()[2]}'<#t>
	        	</#list>
	        	</p>
			<#break/>
		</#switch>
	</#list>
</#macro>
<#macro showreq root lvl path problems>
	<#list problems as req, errors>
	<#if req.getType() == "Requirement" || req.getType() == "TextNode">
		<#local text = ""/>
			<#local isHeader = coreUtils.isHeader(req)/>
		<#if (errors?? && errors?size>0&&errors['problem_rule_error']??&&errors['problem_rule_error']?size>0)>
			<#local fid = "0"/>
			<#if req.attrs().getStringAttributeValue("%SeqID")??>
				<#local fid = req.attrs().getStringAttributeValue("%SeqID")/>
			</#if>
			<#local tp = "Требование"/>
			<#if isHeader>
				<#local tp = "Заголовок"/>
			<#else>
				<#if req.attrs().getStringAttributeValue("Вид")??>
					<#local tp = req.attrs().getStringAttributeValue("Вид")/>
				</#if>
			</#if>
			<#if isHeader>
    			<@showpath req path/>
				<#local text = "<b>"+reqPath+" "+req.getUserVisibleName()+"</b>"/>
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
				<#if name == "%SeqID">
					<td class="col_${name_index}">${fid}</td>
				<#else>
					<#if name == "Вид">
					<td class="col_${name_index}">${utils.refineValue(tp,name,selFormula[name_index])}</td>
					<#else>
						<td class="col_${name_index}">${utils.refineValue(req.attrs().getStringAttributeValue(name, ""),name,selFormula[name_index])}</td>
					</#if>
				</#if>
			</#list>
			<td>
					<a name="${req.getUUId()}"/><#if req.getSUUId()!=fid><a name="${fid}"/></#if>
					<@showProblems req errors/>
			</td>
			</tr>
	    </#if>
				</#if>
			</#list>
</#macro>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
<head>
  <meta http-equiv="content-type" content="text/html; charset=utf-8"/>
  <title></title>
  <style type="text/css">
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
    .toc-prob{
      background-color:pink;
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
	<#assign selAttrs=["%SeqID", "Вид", "_uservisibleid"]/>
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
  <#assign selAttrsNames=utils.getAttrVis(rootReq.getTreeDB(),selAttrs)/>
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
${utils.callMassCheckersReset(rootReq)}
<@showTabs rootReq/>
<@showheader rootReq/>
${utils.initMainGeneration()}
<h3>Содержание</h3>
<div>
<ul class='toc'>

<#assign propedProblemsPair = utils.getProblemedNodes(rootReq.getTreeDB(), rootReq)/>
<#assign propedProblems = propedProblemsPair.getSecond()/>
<#assign relatedNodes = propedProblemsPair.getFirst()/>
<@showheading rootReq 0 "" propedProblems relatedNodes/>
</ul>
</div>
<hr>
<h2>Список обнаруженных нарушений:</h2>
<#assign problems = utils.getCheckerProblems(rootReq.getTreeDB(), rootReq)/>

<#if propedProblems?? && (propedProblems?size>0)>
	<table><tbody><tr class="theader">
	<#list selAttrsNames as name>
		<th class="col_${name_index}">${name}</th>
	</#list>
	<th>Нарушения</th>
	</tr>
	<@showreq rootReq 0 "" problems/>
	</tbody></table>
<#else>
    <#assign nfHead=utils.getVarMeta(rootReq, "DocumentSubTitle", [""]).getValue()!""/>
	<p>Нарушений не обнаружено<#if rootReq.getWorkParent()??&&rootReq.getWorkParent().getWorkParent()??> в поддереве '<#if nfHead!="">${nfHead}<#else>${rootReq.getUserVisibleName()}</#if>'</#if>.</p>
</#if>
<!-- ${.now?iso_local} -->
</body>
</html>