SELECT name,idx,severity,value FROM stats WHERE value>0 AND severity!='info';
