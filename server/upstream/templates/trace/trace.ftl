<#--
/* Copyright 2009-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
-->
<#assign firstPage = true/>
<#assign list = []/>
<#assign washere = []/>
<#include "../lib/ruconsts.ftl" />
<#include "../lib/comInfoTable.ftl" />
<html>
<head>
<meta charset="UTF-8">

<style>

.maintable{
	background: white;
	border-collapse: collapse;
	box-shadow: 5px 5px 10px rgba(122, 122, 122, 0.5);
	margin: 15px;
}

.maintable td, th{
	font-size:1em;
	padding:3px 7px 2px 7px;
	vertical-align: top;
	text-align: justify;
	border-bottom: 1px solid black;
	color:#000000;
}

.maintable th {
	text-align:center;
	padding: 5px 0 5px 0;
	background: rgb(204,204,204);
	background: -moz-linear-gradient(top,  rgba(204,204,204,1) 0%, rgba(238,238,238,1) 100%); /* FF3.6+ */
	background: linear-gradient(to bottom,  rgba(204,204,204,1) 0%,rgba(238,238,238,1) 100%); /* W3C */
}

.block{}

.hidden{
	overflow: hidden;
}

 /* Tooltip container */
.tooltip {
    position: relative;
    display: inline-block;
    border-bottom: 1px dotted black; 
}

/* Tooltip text */
.tooltip .tooltiptext {
    visibility: hidden;
    width: 600px;
    background-color: #555;
    color: #fff;
    padding: 5px 0;
    border-radius: 6px;

    /* Position the tooltip text */
    position: absolute;
    z-index: 1;
	margin:2px;
	
    /* Fade in tooltip */
    opacity: 0;
    transition: opacity 1s;
}

.tright .ttright {
    right: 100%;
}
.tleft .ttleft {
    left: 100%;
}

/* Show the tooltip text when you mouse over the tooltip container */
.tooltip:hover .tooltiptext {
    visibility: visible;
    opacity: 1;
} 

.etc{
	overflow: hidden;
	height: 0px;
	vertical-align: top;
	text-align: center;
	font: bold italic 110% serif; 
}

.attribute{
	font: bold 100% monospace;
}

.button{
	width: 100px;
	cursor: pointer; cursor: hand;
}

.side{
	max-width: 200px;
}

.middle{
	max-width: 800px;
}

body{
	min-width: 560px;
}

#header {
   height: 70px;
   border-radius: 5px 5px 0 0;
   background: rgb(204,204,204);
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
   background: rgb(245,246,246);
   background: -moz-linear-gradient(top,  white 0%, rgba(219,220,226,1) 54%, rgba(184,186,198,1) 97%, rgba(184,186,198,1) 97%); /* FF3.6+ */
   background: linear-gradient(to bottom,  white 0%,rgba(219,220,226,1) 54%,rgba(184,186,198,1) 97%,rgba(184,186,198,1) 97%); /* W3C */
}
<#include "../lib/comInfoCSS.ftl" />
<#include "../lib/projecttabscss.ftl" />
</style>

<script>
function toggle(id)
{ 
	var event = window.event || arguments.callee.caller.arguments[0];
	var target = event.target || event.srcElement;
	if (target.nodeName == 'A') {
		return false;
	}
	var elmheight = document.getElementById(id).offsetHeight;
	var e = document.getElementsByName(id);

	for (i = 0; i < e.length; i++) {
		if (e[i].className == "hidden"){
			e[i].className = "block";
			e[i].style.height = 'auto';
		}else if(e[i].className == "block" && e[i].parentNode.offsetHeight > 160){
			e[i].className = "hidden";
			e[i].style.height = '160px';
		}
		else if(e[i].className == "etc" && e[i].parentNode.offsetHeight > 160){
			e[i].style.height = e[i].offsetHeight*(-1)+20;
		}
	}
} 

function expandAll()
{
	var e = document.getElementsByClassName("hidden");
	while (e.length > 0) {
		e[0].style.height = 'auto';
		e[0].className = "block";
	} 
	e = document.getElementsByClassName("etc");
	for (i = 0; i < e.length; i++) {
		e[i].style.height = 0;
	}
}

function retractAll()
{
	var e = document.getElementsByClassName("block");
	while (e.length > 0) {
		if(e[0].parentNode.offsetHeight > 160){
			e[0].style.height = 160;	
		}
		e[0].className = "hidden";	
	}
	e = document.getElementsByClassName("etc");
	for (i = 0; i < e.length; i++) {
		if(e[i].offsetHeight == 0 && e[i].parentNode.offsetHeight > 160){
			e[i].style.height = '20px';
		}
	}
}
</script>

</head>
<body onload="retractAll()">
<#macro showattrs node>
	<#assign links = utils.getLinks(node)/>
  	<#list links?keys as key>
	   		<#if utils.filter(node, attrFilter, key)>
	   			<#local isuseless=false/>
	   			<#local vals = links?keys/>
	   			<#list vals as val>
		   			<#local sval = val/>
		   			<#local addval = ""/>
		   			<#if links[val]??>
			   			<#local links = node.getLinksOrErrorsValues(key)/>
		   				<#list links as link>
		   					<#if (link.hasErrorsOnResolve(node.attrs()))>
		   						<#local sval = "<font color='red'>"+link.getRaw()+"</font>"/>
		   					<#else>
		   						<#local sval = ""/>
			   					<#local nodes = link.getNodes(node.attrs())/>
			   					<#list nodes as node1>
			   						<#if (sval?length>0)>
			   							<#local sval = sval + ", "/>
			   						</#if>
				   					<#local sval=sval+node1.getFullName(true)/>
				   				</#list>
		   					</#if>
			   			</#list>
		   			</#if>
		   			<#if addval!="">
		   				<#local addval = " "+addval/>
		   			</#if>
		   			<span class="attribute"><a id="${node.getQualifiedId()}-attribute-${key}"></a>${key}: ${sval}${addval}</span><br/>
	   			</#list>
	   		</#if>
	   	</#list>
</#macro>
<#macro showelaboration>
<#local columns = trace.getColumnNames()/>

<table class="maintable">
	<tr><th class="side">${trace.getRevertName(true, rootReq, filter)}</th><th class="middle">element</th><th class="side">${trace.getDirectName(true, rootReq, filter)}</th></tr>

<#list columns as rname>

<#if !rname??>
	<#local rname = "*"/>
</#if>


<tr><td class="side"> 

<div onclick="toggle('${rname}')" name="${rname}" class="block">
<#assign rt = trace.getRevertTraces(rname)/>
<#list rt as name>
	<span class="tooltip tleft">
		<#assign link = ("#"+name.getKey())/>
		<span class="tooltiptext ttleft">
			<#assign node=trace.getNode(name.getKey())!"">
			<#if node!="">
				<#assign link = (utils.getTemplFileName(node)+"#"+utils.getLocalName(name.getKey()))/>
				<@showattrs node/>
				<#if node.getDescription(reportLocation)?has_content>
					${utils.htmlSpaces(node.getDescription(reportLocation))?replace("\n","<br/>")}<br>
				</#if>
			</#if>
		</span>
		<b><a href="${link}">${name.getKey()}[${trace.getCombinedKeys(name.getValue())}]</a></b><br>
	</span>
</#list>
</div>

</td><td class="middle"> 

<div onclick="toggle('${rname}')" id="${rname}" name="${rname}" class="block">
	<b>${rname}</b><br>
	<#if trace.getNode(rname)??>
		<#assign node = trace.getNode(rname)>
		<@showattrs node/>
		<#if node.getDescription(reportLocation)?has_content>
			${utils.htmlSpaces(node.getDescription(reportLocation))?replace("\n","<br/>")}
		</#if>
	</#if>
</div>
<div onclick="toggle('${rname}')" name="${rname}" class="etc">
...
</div>

</td><td class="side">

<div onclick="toggle('${rname}')" name="${rname}" class="block"> 
<#assign tr=trace.getTraces(rname)/>
<#list tr as name>
	<span class="tooltip tright">
		<#assign link = ("#"+name.getKey())/>
		<span class="tooltiptext ttright">
			<#assign node=trace.getNode(name.getKey())!""/>
			<#if node!="">
				<#assign link = (utils.getTemplFileName(node)+"#"+utils.getLocalName(name.getKey()))/>
				<@showattrs node/>
				<#if node.getDescription(reportLocation)?has_content>
					${utils.htmlSpaces(node.getDescription(reportLocation))?replace("\n","<br/>")}<br>
				</#if>
			</#if>
		</span>
		<b><a href="${link}">${name.getKey()}[${trace.getCombinedKeys(name.getValue())}]</a></b><br>
	</span>
</#list> 
</div>

</td></tr>

</#list>
</table>
</#macro>

<#if !filter??>
<#assign filter = "*"/>
</#if>

<#if !what??>
<#assign what = "reference"/>
</#if>
<div id="header"><h1>Requality Project</h1></div> 
<@showheader rootReq/>
<hr>
${utils.initMainGeneration()}<#t/>
<button onclick="expandAll()" class="button">Expand all</button><button onclick="retractAll()" class="button">Retract all</button>
<hr>

<#if !attrFilter??>
	<#assign attrFilter = ['Вид','Производное']/>
</#if>
${trace.initData(utils, rootReq,type1,type2,what,filter,traceabilitySettings,userData)} 
<@showelaboration />
<#if root.getError() != "">
	<errors><![CDATA[${utils.htmlEscape(root.getError())}]]></errors>
</#if>
<div id="footer">${progress.getDateTime()}</div>

<!-- ${.now?iso_local} -->
</body>
</html>