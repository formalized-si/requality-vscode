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
*		1. dateFrom & dateTo - format "EEE MMM dd HH:mm:ss zzz yyyy"
*		2. step - period (year, month, week, day) and step for a cell, format "1 day", "2 week", "1 year"
*		3. scaleX, scaleY 10 - 1000
*		4. autoscale - true or false to use size automatic or user's scale
*/
 -->
<#macro showprogress>

	<#if !autoscale??>
		<#assign autoscale = "true"/>
	</#if>	
	<#assign path = statman.getPath() />
	
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
	<p><a href="./${utils.getTemplFileName(rootReq,'stat.ftl')}">Tabular Data</a></p> 

		<#if !dateFrom?? && !dateTo??>
			<#if !statman.isInfoExist()>	
				${statman.getDataRecord()}
			</#if>
			${progress.readReport(path)}	

			<#if coverage.loadStorage()>		
				<h2>Number of elements</h2>
				<table class="graphictable">
					<tr><th>${progress.generateGraphic("count", scaleX, scaleY, autoscale)}
						<#if progress.getOverlap()>
							${progress.createCoverGraph()}
							<img onclick="circleBySegment('count','cc','ncc','lc',${progress.getFunctionString(1)})" style="max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index:100;" alt="Graphic" src="./graphics/cover.png"/>
							<img onclick="changeImage('cc','count')" style="max-width:900px; max-height:900px; position: absolute; top: ${progress.getImgHeight(90)}px; left: 10px; z-index:100;" alt="Graphic" src="./graphics/leg.png"/>
							<img onclick="changeImage('ncc','count')" style="max-width:900px; max-height:900px; position: absolute; top: ${progress.getImgHeight(105)}px; left: 10px; z-index:100;" alt="Graphic" src="./graphics/leg.png"/>
							<img onclick="changeImage('lc','count')" style="max-width:900px; max-height:900px; position: absolute; top: ${progress.getImgHeight(120)}px; left: 10px; z-index:100;" alt="Graphic" src="./graphics/leg.png"/>
						</#if>
					</tr></th>
				</table>					
				<h2>Coverage in percent</h2>
				<table class="graphictable">
					<tr><th>${progress.generateGraphic("percent", scaleX, scaleY, autoscale)}
						<#if progress.getOverlap()>
							${progress.createCoverGraph()}
							<img onclick="circleBySegment('percent','cp','ncp','',${progress.getFunctionString(2)})" style="max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index:100;" alt="Graphic" src="./graphics/cover.png"/>
							<img onclick="changeImage('cp','percent')" style="max-width:900px; max-height:900px; position: absolute; top: ${progress.getImgHeight(90)}px; left: 10px; z-index:100;" alt="Graphic" src="./graphics/leg.png"/>
							<img onclick="changeImage('ncp','percent')" style="max-width:900px; max-height:900px; position: absolute; top: ${progress.getImgHeight(105)}px ; left: 10px; z-index:100;" alt="Graphic" src="./graphics/leg.png"/>
						</#if>
					</tr></th>
				</table>
			</#if>
			<h2>Number of requirements & test purposes</h2>
			<table class="graphictable">
				<tr><th>${progress.generateGraphic("amount", scaleX, scaleY, autoscale)}
					<#if progress.getOverlap()>
						${progress.createCoverGraph()}
						<img onclick="circleBySegment('amount','tpc','rqc','',${progress.getFunctionString(3)})" style="max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index:100;" alt="Graphic" src="./graphics/cover.png"/>
						<img onclick="changeImage('rqc','amount')" style="max-width:900px; max-height:900px; position: absolute; top: ${progress.getImgHeight(90)}px;; left: 10px; z-index:100;" alt="Graphic" src="./graphics/leg.png"/>
						<img onclick="changeImage('tpc','amount')" style="max-width:900px; max-height:900px; position: absolute; top: ${progress.getImgHeight(105)}px; left: 10px; z-index:100;" alt="Graphic" src="./graphics/leg.png"/>
					</#if>
				</tr></th>
			</table>
		<#else>	
				<#if statman.isDateCorrect(dateFrom, dateTo)>
					<#if !statman.isInfoExist()>	
						${statman.getDataRecord()}
					</#if>

					${progress.readReport(path)}			
					<#if coverage.loadStorage()>	
						<h2>Number of elements</h2>
						<table class="graphictable">
							<tr><th>${progress.generateGraphicSpecial("count", dateFrom, dateTo, step, scaleX, scaleY, autoscale)}
							<#if progress.getOverlap()>
								${progress.createCoverGraph()}
								<img onclick="circleBySegment('count','cc','ncc','lc',${progress.getFunctionString(1)})" style="max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index:100;" alt="Graphic" src="./graphics/cover.png"/>
								<img onclick="changeImage('cc','count')" style="max-width:900px; max-height:900px; position: absolute; top: ${progress.getImgHeight(115)}px; left: 10px; z-index:100;" alt="Graphic" src="./graphics/leg.png"/>
								<img onclick="changeImage('ncc','count')" style="max-width:900px; max-height:900px; position: absolute; top: ${progress.getImgHeight(130)}px; left: 10px; z-index:100;" alt="Graphic" src="./graphics/leg.png"/>
								<img onclick="changeImage('lc','count')" style="max-width:900px; max-height:900px; position: absolute; top: ${progress.getImgHeight(145)}px;; left: 10px; z-index:100;" alt="Graphic" src="./graphics/leg.png"/>
							</#if>
							</tr></th>
						</table>						
						<h2>Coverage in percent</h2>
						<table class="graphictable">
							<tr><th>${progress.generateGraphicSpecial("percent", dateFrom, dateTo, step, scaleX, scaleY, autoscale)}
							<#if progress.getOverlap()>
								${progress.createCoverGraph()}
								<img onclick="circleBySegment('percent','cp','ncp','',${progress.getFunctionString(2)})" style="max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index:100;" alt="Graphic" src="./graphics/cover.png"/>
								<img onclick="changeImage('cp','percent')" style="max-width:900px; max-height:900px; position: absolute; top: ${progress.getImgHeight(115)}px;; left: 10px; z-index:100;" alt="Graphic" src="./graphics/leg.png"/>
								<img onclick="changeImage('ncp','percent')" style="max-width:900px; max-height:900px; position: absolute; top: ${progress.getImgHeight(130)}px; left: 10px; z-index:100;" alt="Graphic" src="./graphics/leg.png"/>
							</#if>
							</tr></th>
						</table>
					</#if>
					<h2>Number of requirements & test purposes</h2>
					<table class="graphictable">
						<tr><th>${progress.generateGraphicSpecial("amount", dateFrom, dateTo, step, scaleX, scaleY, autoscale)}
						<#if progress.getOverlap()>
							${progress.createCoverGraph()}
							<img onclick="circleBySegment('amount','tpc','rqc','',${progress.getFunctionString(3)})" style="max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index:100;" alt="Graphic" src="./graphics/cover.png"/>
							<img onclick="changeImage('rqc','amount')" style="max-width:900px; max-height:900px; position: absolute; top: ${progress.getImgHeight(115)}px; left: 10px; z-index:100;" alt="Graphic" src="./graphics/leg.png"/>
							<img onclick="changeImage('tpc','amount')" style="max-width:900px; max-height:900px; position: absolute; top: ${progress.getImgHeight(130)}px; left: 10px; z-index:100;" alt="Graphic" src="./graphics/leg.png"/>
						</#if>
						</tr></th>
					</table>
				<#else>
					Wrong date or format. Date 'from' can't be equals or after date 'to'. Format "EEE MMM dd HH:mm:ss zzz yyyy".
				</#if>
			</#if>	
	
		<p><a href="./${utils.getTemplFileName(rootReq,'stat.ftl')}">Tabular Data</a></p> 
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

h2{
	font: bold 20px Arial serif;
	margin: 10px;
	text-shadow: 1px 1px 0 #8E9FA5;
}

.graphictable{
   background: white;
   border-collapse:collapse;
   box-shadow: 5px 5px 10px rgba(122, 122, 122, 0.5);
   margin: 0 0 50px 15px;
   position: relative;
   border:1px outset black;
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

function circleBySegment(group, id1, id2, id3, x1, y1, y2, y3){
 var e = window.event || arguments.callee.caller.arguments[0];
 var x = e.offsetX==undefined?e.layerX:e.offsetX;
 var y = e.offsetY==undefined?e.layerY:e.offsetY;
 y = e.target.clientHeight - y;
 var radius = 10;

  	for(var i = 0;i<y1.length-1;i++){
		  a = (x1[i+1] - x1[i]) * (x1[i+1] - x1[i]) + (y1[i+1] - y1[i]) * (y1[i+1] - y1[i]);
		  b = 2 * ((x1[i+1] - x1[i]) * (x1[i] - x) + (y1[i+1] - y1[i]) * (y1[i] - y));
		  c = x * x + y * y + x1[i] * x1[i] + y1[i] * y1[i] - 2 * (x * x1[i] + y * y1[i]) - radius * radius;		 		 
		 if ( - b < 0)
		 {
			if (c < 0) {
				changeImage(id1, group);
				break;
			}
		 }
		 if ( - b < (2 * a))
		 {
			if (4 * a * c - b * b < 0){
				changeImage(id1, group);
				break;
			}
		 }
		if (a + b + c < 0) {
			changeImage(id1, group);
			break;
		}
		
		  a = (x1[i+1] - x1[i]) * (x1[i+1] - x1[i]) + (y2[i+1] - y2[i]) * (y2[i+1] - y2[i]);
		  b = 2 * ((x1[i+1] - x1[i]) * (x1[i] - x) + (y2[i+1] - y2[i]) * (y2[i] - y));
		  c = x * x + y * y + x1[i] * x1[i] + y2[i] * y2[i] - 2 * (x * x1[i] + y * y2[i]) - radius * radius;
		 if ( - b < 0)
		 {
			if (c < 0) {
				changeImage(id2, group);
				break;
			}
		 }
		 if ( - b < (2 * a))
		 {
			if (4 * a * c - b * b < 0){
				changeImage(id2, group);
				break;
			}
		 }
		if (a + b + c < 0) {
			changeImage(id2, group);
			break;
		}
		
		if(y3){
			a = (x1[i+1] - x1[i]) * (x1[i+1] - x1[i]) + (y3[i+1] - y3[i]) * (y3[i+1] - y3[i]);
			b = 2 * ((x1[i+1] - x1[i]) * (x1[i] - x) + (y3[i+1] - y3[i]) * (y3[i] - y));
			c = x * x + y * y + x1[i] * x1[i] + y3[i] * y3[i] - 2 * (x * x1[i] + y * y3[i]) - radius * radius;
			if ( - b < 0)
			{
				if (c < 0) {
					changeImage(id3, group);
					break;
				}
			}
			if ( - b < (2 * a))
			{
				if (4 * a * c - b * b < 0){
					changeImage(id3, group);
					break;
				}
			}
			if (a + b + c < 0) {
				changeImage(id3, group);
				break;
			}
		}
	}
};
	
function changeImage(name, group){ 
	var images = document.getElementsByClassName(group);
	var graph = document.getElementById(name);
	for(var i = 0;i<images.length;i++){
		if(graph.style.zIndex < images[i].style.zIndex){
			images[i].style.zIndex = images[i].style.zIndex - 10;
		}
	}
	graph.style.zIndex = 30;
};

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
};
</script>
</head>
<body>

${utils.initMainGeneration()}<#t/>
<div id="header"><h1>Requality Project</h1></div>	
<#if coverageFilePath?? || coverageStorageType??>
	<#if !coverage.loadStorage()>	
		<#if coverageFilePath??>
			Warning: Coverage information cannot be loaded from ${coverageFilePath}. Errors: ${coverage.getLastErrors()}
		<#else>
			Warning: Coverage information cannot be loaded from chosen source. Errors: ${coverage.getLastErrors()}
		</#if>
	<#else>
		<@showprogress/>
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