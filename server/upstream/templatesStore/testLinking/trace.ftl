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
<html>
<head>
<meta charset="UTF-8">

<style>

.hidden{
	overflow: hidden;
}
.block{
}
.etc{
	overflow: hidden;
	height: 0px;
	vertical-align: top;
	text-align: center;
	font: bold italic 110% serif; 
}

.button{
	width: 100px;
	cursor: pointer; cursor: hand;
}

.table{
	min-width: 1000px;
	max-width: auto;
	background: white;
	border-collapse: collapse;
	box-shadow: 5px 5px 10px rgba(122, 122, 122, 0.5);
	margin: 15px;
}

.table td, .table th{
	width: 33%;
	font-size:1em;
	padding:3px 7px 2px 7px;
	vertical-align: top;
	text-align: justify;
	border-bottom: 1px solid black;
	color:#000000;
}
.table th {
	text-align:center;
	padding-top:5px;
	padding-bottom:4px;
	background: rgb(204,204,204); /* Old browsers */
	background: -moz-linear-gradient(top, rgba(204,204,204,1) 0%, rgba(238,238,238,1) 100%); /* FF3.6+ */
	background: -webkit-gradient(linear, left top, left bottom, color-stop(0%,rgba(204,204,204,1)), color-stop(100%,rgba(238,238,238,1))); /* Chrome,Safari4+ */
	background: -webkit-linear-gradient(top, rgba(204,204,204,1) 0%,rgba(238,238,238,1) 100%); /* Chrome10+,Safari5.1+ */
	background: -o-linear-gradient(top, rgba(204,204,204,1) 0%,rgba(238,238,238,1) 100%); /* Opera 11.10+ */
	background: -ms-linear-gradient(top, rgba(204,204,204,1) 0%,rgba(238,238,238,1) 100%); /* IE10+ */
	background: linear-gradient(to bottom, rgba(204,204,204,1) 0%,rgba(238,238,238,1) 100%); /* W3C */
	filter: progid:DXImageTransform.Microsoft.gradient( startColorstr='#cccccc', endColorstr='#eeeeee',GradientType=0 ); /* IE6-9 */
}

#container {
	min-width: 1024px;
	max-width: auto;
	background: white; 
}

#header {
	height: 70px;
	width: 1024px auto;
	border-radius: 5px 5px 0 0;
	background: rgb(204,204,204);
	background: -moz-linear-gradient(top, rgba(204,204,204,1) 0%, white 100%); /* FF3.6+ */
	background: -webkit-gradient(linear, left top, left bottom, color-stop(0%,rgba(204,204,204,1)), color-stop(100%, white)); /* Chrome,Safari4+ */
	background: -webkit-linear-gradient(top, rgba(204,204,204,1) 0%, white 100%); /* Chrome10+,Safari5.1+ */
	background: -o-linear-gradient(top, rgba(204,204,204,1) 0%, white 100%); /* Opera 11.10+ */
	background: -ms-linear-gradient(top, rgba(204,204,204,1) 0%, white 100%); /* IE10+ */
	background: linear-gradient(to bottom, rgba(204,204,204,1) 0%, white 100%); /* W3C */
	filter: progid:DXImageTransform.Microsoft.gradient( startColorstr='#cccccc', endColorstr='white',GradientType=0 ); /* IE6-9 */
}

#header h1{
	font: bold 36px Arial serif;
	margin: 0px;
	padding-top:10px;
	text-align: center;
	text-shadow: 1px 1px 0 #8E9FA5;
}

#footer {
	height: 30px;
	width: 1024px auto;
	text-align: right;
	padding-top:10px;
	border-radius: 0 0 5px 5px;
	background: rgb(245,246,246);
	background: -moz-linear-gradient(top, white 0%, rgba(219,220,226,1) 54%, rgba(184,186,198,1) 97%, rgba(184,186,198,1) 97%); /* FF3.6+ */
	background: -webkit-gradient(linear, left top, left bottom, color-stop(0%, white), color-stop(54%,rgba(219,220,226,1)), color-stop(97%,rgba(184,186,198,1)), color-stop(97%,rgba(184,186,198,1))); /* Chrome,Safari4+ */
	background: -webkit-linear-gradient(top, white 0%,rgba(219,220,226,1) 54%,rgba(184,186,198,1) 97%,rgba(184,186,198,1) 97%); /* Chrome10+,Safari5.1+ */
	background: -o-linear-gradient(top, white 0%,rgba(219,220,226,1) 54%,rgba(184,186,198,1) 97%,rgba(184,186,198,1) 97%); /* Opera 11.10+ */
	background: -ms-linear-gradient(top, white 0%,rgba(219,220,226,1) 54%,rgba(184,186,198,1) 97%,rgba(184,186,198,1) 97%); /* IE10+ */
	background: linear-gradient(to bottom, white 0%,rgba(219,220,226,1) 54%,rgba(184,186,198,1) 97%,rgba(184,186,198,1) 97%); /* W3C */
	filter: progid:DXImageTransform.Microsoft.gradient( startColorstr='white', endColorstr='#b8bac6',GradientType=0 ); /* IE6-9 */
}
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
<#macro showelaboration req>
	<#if trace.linksCorrects(req,"Вид","Описание")>
		<h2>${req.getFullName()}</h2><br/>
		<#if req.getDescription()??>
			<div>${req.getDescription()}</div>
		<#else>
			<div></div>
		</#if>
	</#if>
	<#list req.getWorkChildren() as chi>
		<@showelaboration chi/>
	</#list>

</#macro>

<#if !what??>
<#assign what = "reference"/>
</#if>

<div id="container">
<div id="header"><h1>Requality Project</h1></div> 
<button onclick="expandAll()" class="button">Expand all</button><button onclick="retractAll()" class="button">Retract all</button>
<hr>
${trace.initData(rootReq,type1,type2,what,filter,traceabilitySettings,userData)} 
<@showelaboration rootReq/>
<#if root.getError() != "">
	<errors><![CDATA[${utils.htmlEscape(root.getError())}]]></errors>
</#if>
<div id="footer">${progress.getDateTime()}</div>
</div>
</body>
</html>