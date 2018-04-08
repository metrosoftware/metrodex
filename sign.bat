if exist jre ( 
    set javaDir=jre\bin\
)

%javaDir%java.exe -Xmx1024m -cp "classes;lib/*;conf" -Dmetro.runtime.mode=desktop metro.tools.SignTransactionJSON
