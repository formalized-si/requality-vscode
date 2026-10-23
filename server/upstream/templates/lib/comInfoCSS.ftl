
.notResolved   {
	background: pink;
	width:bold;
	background-color: pink; 

 }
.notResolved polygon{
	fill:pink;
}
#reportheader {
	border-collapse: collapse;
	border-radius:5px 5px 0px 0px; 
	border: outset 1px grey; 
    text-align: center;
    padding: 5px 0 5px 0;
	background: rgb(204,204,204);
	background: -moz-linear-gradient(top,  rgba(204,204,204,1) 0%, rgba(238,238,238,1) 100%); /* FF3.6+ */
	background: linear-gradient(to bottom,  rgba(204,204,204,1) 0%,rgba(238,238,238,1) 100%); /* W3C */
}

#block {
	width:auto;
	clear:both; 
	background:rgba(184,186,198, 0.1); 
	border: outset 1px silver;
	border-top:none;
	border-radius: 0px 0px 5px 5px;
	box-shadow: 5px 5px 10px rgba(122, 122, 122, 0.5);
}

.pttab {
    overflow: hidden;
    background-color: #f1f1f1;
}
.pttab span {
    background-color: inherit;
    float: left;
    border: none;
    outline: none;
    cursor: pointer;
    padding: 14px 16px;
    transition: 0.3s;
}

.table-hidden{
  height:0px;
  overflow: hidden;
  border-collapse: collapse;
}

.changesinfo ul {
	list-style-type: none;
	margin-top: 0px;
	margin-left:-30px;
	margin-right: 10px;
}

.changesinfo li {
	padding-left: 15px;
}

.comInfo{
	border-collapse: collapse;
	width: 100%;
	border: 0;
}

.comInfo th {
	align: enter;
	background: #eeeeee;
}

.comInfo td {
	padding: 5px 0px 5px 15px;
}

.comInfo tr:nth-child(odd) { 
	background-color: #fff; 
}

.comInfo td, .comInfo th {
  border: 1px solid grey;
}

.comInfo tr:last-child td {
  border-bottom: 0;
}
.comInfo tr td:first-child,
.comInfo tr th:first-child {
  border-left: 0;
}
.comInfo tr td:last-child,
.comInfo tr th:last-child {
  border-right: 0;
}