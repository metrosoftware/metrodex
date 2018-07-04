**Instalation:**
<pre>
git clone https://github.com/metrosoftware/metrodex.git
cd metrodex
git checkout v0.0.1.testnet.branch
./compile.sh
</pre>

**Start:**
<pre>
./start.sh
</pre>

**Open:**

http://localhost:6886/


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
 
<pre>
./miner.sh
</pre>
