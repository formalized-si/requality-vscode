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
 
<#macro showstat>
	${statman.refreshRecordsList()}
	<#if statman.getErrorMsg()?has_content>
		<#list statman.getErrorMsg()?keys as key> 
			Error: ${key}
			<div onClick="toggle(this)">
				<img width="20px" height="20px" src="data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs%3D" onLoad="this.onload=null; this.src=get_png_image_src(0);"/>
				<div style="display: block">
				${statman.getErrorMsg()[key]}
				</div>
			</div>  
		</#list> 
	<#else>
		<p><a href="./${utils.getTemplFileName(rootReq,'progress.ftl')}">Graphics Data</a></p> 
		<#assign path = statman.getPath() />
		
		${progress.readReport(path)}
		<#if progress.getarrayRevision()?has_content>
		<#assign revs = progress.getarrayRevision()/>
		<#assign dates = progress.getarrayDate()/>
		<#if !dateFrom?? && !dateTo??>		
			<table class="maintable">
				<tr><th>Revision</th><th>Measure</th><th>Requirements</th><th>Test purposes</th><th>Leaves</th><#if coverage.loadStorage()><th>Covered</th><th>Not covered</th></#if></tr>
				<#list 0..progress.getarrayDate()?size-1 as i>
					<#assign rev = progress.getArrayData(i)/>
					<tr>
						<td <#if coverage.loadStorage()>rowspan="2"</#if>>${revs[i]}</td><td>Count</td>
						<td>${rev["number of requirements"]}</td>
						<td>${rev["number of test purposes"]}</td>
						<td>${rev["number of leaves"]}</td>
					<#if coverage.loadStorage()>
						<td>${rev["number coverage (green status)"]}</td>
						<td>${rev["number coverage (red status)"]}</td>
					</tr>									
					<tr>
						<td>Percent</td>
						<td>-</td>
						<td>-</td>
						<td>-</td>
						<td>${rev["percent coverage (green status)"]}%</td>
						<td>${rev["percent coverage (red status)"]}%</td>
					</#if>
					</tr>				
				</#list> 
			</table>
		<#else>
			<#if progress.setBeginEnd(dateFrom, dateTo)>
			<table class="maintable">
			<tr><th>Date</th><th>Measure</th><th>Requirements</th><th>Test purposes</th><th>Leaves</th><#if coverage.loadStorage()><th>Covered</th><th>Not covered</th></#if></tr>
				<#list progress.getBegin()..progress.getEnd() as i>
				<#assign rev = progress.getArrayData(i)/>
					<tr>
						<td <#if coverage.loadStorage()>rowspan="2"</#if>>${dates[i]}</td><td>Count</td>
						<td>${rev["number of requirements"]}</td>
						<td>${rev["number of test purposes"]}</td>
						<td>${rev["number of leaves"]}</td>
					<#if coverage.loadStorage()>
						<td>${rev["number coverage (green status)"]}</td>
						<td>${rev["number coverage (red status)"]}</td>
					</tr>
					<tr>
						<td>Percent</td>
						<td>-</td>
						<td>-</td>
						<td>-</td>
						<td>${rev["percent coverage (green status)"]}%</td>
						<td>${rev["percent coverage (red status)"]}%</td>
					</#if>
					</tr>				
				</#list> 
			</table>
			</#if>			   
		</#if>	
		</#if>
		<p><a href="./${utils.getTemplFileName(rootReq,'progress.ftl')}">Graphics Data</a></p>
	</#if> 		
</#macro>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
<style type="text/css">
a{
	margin:10px;
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

.block {
	width:auto;
	clear:both; 
	padding:10px; 
	background:rgba(184,186,198, 0.1); 
	border: outset 1px #1e90ff;
	border-top:none;
	border-left:dotted 1px #1e90ff; 
	border-radius: 0px 0px 5px 5px;
	box-shadow: 5px 5px 10px rgba(122, 122, 122, 0.5);
}

.maintable{
   background: white;
   border-collapse:collapse;
   box-shadow: 5px 5px 10px rgba(122, 122, 122, 0.5);
   margin: 15px;
}

.maintable td, th{
   font-size:1em;
   border:1px outset black;
   padding:3px 7px 2px 7px;
   color:#000000;
}

.maintable th {
   text-align:center;
   padding-top:5px;
   padding-bottom:4px;
   background: rgb(204,204,204); /* Old browsers */
   background: -moz-linear-gradient(top,  rgba(204,204,204,1) 0%, rgba(238,238,238,1) 100%); /* FF3.6+ */
   background: linear-gradient(to bottom,  rgba(204,204,204,1) 0%,rgba(238,238,238,1) 100%); /* W3C */
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
</style>

<script type="text/javascript">
const ops = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAlgAAAJYCAYAAAC+ZpjcAAAABHNCSVQICAgIfAhkiAAAAAlwSFlzAABYlQAAWJUB2W030wAAABl0RVh0U29mdHdhcmUAd3d3Lmlua3NjYXBlLm9yZ5vuPBoAAA30SURBVHic7d2xj2V3fYfh7zljQyAUyIviAgzdWkAFBYbFBXaQG0DQpIlEGkRHD1ISxRJdUvI/IIoUkZDM3JmxVgJlRWGJlj8AicrNlmvtPRQYEivreNf7ztydu8/TTDP33k/56nfOvWeZI3Dnzp2P3b179+a6rje3bXtxWZYXl2X59H6/v7Esy42Z+fjMfPTdvwAAl2o59IBHtW3bstvtvnBycvLqtm2vzMyXZuazM7MeeBoAwMxck8C6uLh4/v79+99d1/XVbdu+MTPPH3oTAMD7eWID64033vjoycnJa8uyfH9mvjczzx56EwDAw3jiAmu32700Mz9YluUfZuaTh94DAPConpjAOj8/f3lmfrxt27cPvQUA4HEcPLAuLi6+ud/vfzozXz30FgCAwsEC6+zs7Osz87P587cAAQCOxpUH1unp6XPruv7bzPxo/LQCAHCETq7qg7ZtW15++eV/WpbllzPzyjwBlycBAC7DlUTOm2+++bn79+//fGZuXcXnAQAc0qWfYO12u+9s2/armbl52Z8FAPAkuLQTrNu3bz9z7969f1mW5V/HvVYAwFPkUgJrt9u9sCzLf87MVy7j/QEAnmR5YJ2fn39+27bdzLxQvzcAwHWQXro7PT39yrZtvx5xBQA8xbKb3C8uLr45M2+M5wcCAE+55ATr/Pz8H/f7/a9m5hPF+wEAXGePfQ/W+fn5t7Zt+6+ZeSbYAwBw7T1WYO12u5eWZXlzZv422gMAcO196MA6PT394rquv56Z58I9AADX3ocKrPPz889u2/bfM/OZeA8AwLX3yDe5v/XWW8/u9/tfjLgCAHigRw6st99++9+XZfnaZYwBADgGj3SJ8N1vDP7yUV8HAPA0eehQevf5gr+bmRuXuAcA4Np7qEuEr7/++rosyy9GXAEAfKCHCqxbt279cGZuXfIWAICj8IGXCC8uLm7s9/vfz8ynrmAPAMC194EnWPv9/j9GXAEAPLT/9wTr7Ozs6zPzmw/6PwAA/sf7nmBt27bMzM9GXAEAPJL3Dayzs7Nvz8yXrnALAMBReN/AWpblJ1c5BADgWDwwsM7Ozv5+/CwDAMCH8sDAWpbln696CADAsfg/N7DvdruXlmX57SHGAAAcgwedYP3gylcAAByR95xg3b59+2/eeeedP87MJw+0BwDg2nvPCda9e/e+O+IKAOCxvCewlmX5/qGGAAAci79eItztdn+3LMsfZubZA+4BALj2/vcJ1vdGXAEAPLa/Bta6rq8ecggAwLFYZ/78YOdt275x4C0AAEdhnZk5Ozv74sw8f+AtAABHYZ2ZWdf1lUMPAQA4Fn+5RCiwAAAif7nJ/csHXQEAcETWO3fufGxmXjj0EACAY7HevXv35jz4oc8AAHwI68nJyYuHHgEAcEzWbdsEFgBAaJ2Zm4ceAQBwTNaZ+cyhRwAAHJN1Zm4cegQAwDFZZ+a5Q48AADgm68x84tAjAACOyTozHzn0CACAYyKwAABi68ycHHoEAMAx8YgcAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAIDYM4cewOV67bXXlkNvAICnjRMsAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAICYwAIAiAksAIDYnwCS5NTZzolVpAAAAABJRU5ErkJggg=='
const png1 = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABQAAAAUCAYAAACNiR0NAAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH3wEMCzYiCCOtIgAAAIlJREFUOMvt080KAWEUxvEfZcFGYitpMgy5OpfCvaCUDOUOXIqlLLwLpslHZvn+l+c5nfM8nQ6RSCV0scbkTU+KLXpFoV7S3EYHK8xK9AGWaKD1rcsEG+wwfqpnOAVt/mv0BGfkYVAfexwKS16ofRiahug3NHHFApd/jjTEMTibVnX5DKP4AJEHd6dzEM+IXu5IAAAAAElFTkSuQmCC',
	png2 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABQAAAAUCAQAAAAngNWGAAAAAmJLR0QAAKqNIzIAAAAJcEhZcwAAAEgAAABIAEbJaz4AAABoSURBVCjP1c/NCkBAGIXhN1nasUXI+Mv1z9BEI8Ud2dpaqPmWnOXp6dSB/yUhk8GJhVECFROGRkJzdhy9hHasaBlNcTjqZxW8woiLgNC317AxU/jYgOVA+ViLxVD5b2hOCYOYUsK+mRuFlREpEzrazAAAACV0RVh0ZGF0ZTpjcmVhdGUAMjAxNS0wOS0wOFQwNzowNTozOS0wNDowMEXWUfsAAAAldEVYdGRhdGU6bW9kaWZ5ADIwMTUtMDktMDhUMDc6MDU6MzktMDQ6MDA0i+lHAAAAAElFTkSuQmCC";

function get_png_image_src(){
 	return png1; 
}

function toggle(div)
{	
var elms = div.getElementsByTagName("*");

for(var i = 0; i < elms.length; i++){
    if (elms[i].tagName == "DIV") {
	
    	if (elms[i].style.display == 'none'){
      		elms[i].style.display = 'block';
    	}else{		
      		elms[i].style.display = 'none';
    	}
    }
    
    if (elms[i].tagName == "IMG") {
       if(elms[i].src==png1){
			elms[i].src = png2;
		}else{
			elms[i].src = png1;
		}
    }
 }
}
</script>
</head>
<body>

<div id="header"><h1>Requality Project</h1></div>
<#if coverageFilePath?? || coverageStorageType??>
	<#if !coverage.loadStorage()>	
		<#if coverageFilePath??>
			Warning: Coverage information cannot be loaded from ${coverageFilePath}. Errors: ${coverage.getLastErrors()}
		<#else>
			Warning: Coverage information cannot be loaded from chosen source. Errors: ${coverage.getLastErrors()}
		</#if>
	<#else>
		<@showstat/>
	</#if>	
<#else>
	There is no source of coverage information.<br>
	If using Eclipse plugin please press 'Update Coverage Source' button on report settings' properties.<br>
	If using console mode please set 'coverageFilePath' parameter as -coverageFilePath="path to coverage info file". 
</#if>		
<div id="footer">${progress.getDateTime()}</div>

<!-- ${.now?iso_local} -->
</body>
</html>