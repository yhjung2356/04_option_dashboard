set YEAR=%date:~2,4%
set MONTH=%date:~7,2%
set DAY=%date:~10,2%

set HOUR=%time:~0,2%
set MINUTE=%time:~3,2%

:loop
	::Initialize GitHub
	git init
	
	::Pull any external changes (maybe you deleted a file from your repo?)
	git pull
	
	::Add all files in the directory
	git add --all
	
	::Commit all changes with the message "auto push". 
	::Change as needed.
	git commit -m "Auto Commit %YEAR%/%MONTH%/%DAY%  %HOUR%:%MINUTE%"
	
	::Push all changes to GitHub 
	git push
	
	::Alert user to script completion and relaunch.
	echo Complete. Relaunching...
	
	::Wait 3600 seconds until going to the start of the loop.
	::Change as needed.
	TIMEOUT 3600
	
::Restart from the top.	
goto loop