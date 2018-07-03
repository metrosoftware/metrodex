**Instalation:**
<pre>
git clone https://github.com/metrosoftware/metrodex.git
cd metrodex
./compile.sh
</pre>

**Start:**
ubuntu:
<pre>
./start.sh
</pre>
windows:
<pre>
metro.exe
</pre>

**Open:**

http://localhost:7886/


**Configuration for mining:** 

<pre>
cd conf
touch metro.properties
echo "metro.mine.serverAddress=localhost" >> metro.properties
echo "metro.mine.publicKey={{your public key}}" >> metro.properties
echo "metro.myAddress={{your external ip}}" >> metro.properties
</pre>

for example:

<pre>
echo "metro.mine.serverAddress=localhost" >> metro.properties
echo "metro.mine.publicKey=3362ed6973cd95305aa7cfceea8bdb7050c67975bc3071fe24a2529c9af2ec58" >> metro.properties
echo "metro.myAddress=217.61.21.146" >> metro.properties
</pre>

**Start cpu mining:**

ubuntu:
<pre>
./miner.sh
</pre>

windows:
<pre>
miner.bat
</pre>
