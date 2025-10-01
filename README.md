# P1 — Step-by-step procedure

## 0) Pre-checks

From your local machine, verify you can SSH to the VM:
```bash
ssh emdwmt@cs6304-emdwmt-01.class.mst.edu
```

Know the local path of the dataset on Windows  
**Example:**  
`C:\Users\emdwmt\Downloads\soc-LiveJournal1Adj.txt`

If SSH uses a nonstandard port, note it (needed for scp).  
You would use `-P <port>` with `scp` if required.

---

## 1) Copy the dataset from Windows → VM

Run this on your Windows terminal (PowerShell), **not inside the VM**:
```powershell
scp "C:\Users\emdwmt\Downloads\soc-LiveJournal1Adj.txt" emdwmt@cs6304-emdwmt-01.class.mst.edu:~/P1/
```

**Git Bash:**
```bash
scp /c/Users/emdwmt/Downloads/soc-LiveJournal1Adj.txt emdwmt@cs6304-emdwmt-01.mst.edu:~/P1/
```

If your SSH uses a custom port (e.g. 2222):
```powershell
scp -P 2222 "C:\Users\emdwmt\Downloads\soc-LiveJournal1Adj.txt" emdwmt@cs6304-emdwmt-01.class.mst.edu:~/P1/
```

If you already see the file on the VM (`ls -lh ~/P1`), skip this step.

---

## 2) Create project folder on VM (run inside the VM)
```bash
mkdir -p ~/P1 && cd ~/P1
```

---

## 3) Put the dataset into HDFS
```bash
export P1_IN_DIR=/user/$USER/p1_input
hdfs dfs -mkdir -p "$P1_IN_DIR"
hdfs dfs -put -f ~/P1/soc-LiveJournal1Adj.txt "$P1_IN_DIR"/
hdfs dfs -ls -h "$P1_IN_DIR"
```

**Expected HDFS path:**  
`/user/<youruser>/p1_input/soc-LiveJournal1Adj.txt`

---

## 4) Create mapper and reducer (in ~/P1)

**Mapper (counts friends per line; emits: friend_count<TAB>1):**
```bash
cat > mapper_p1.py << 'PY'
#!/usr/bin/env python3
import sys, re
splitter = re.compile(r'[\t ]+')
for line in sys.stdin:
    line = line.strip()
    if not line: continue
    parts = splitter.split(line, maxsplit=1)
    if len(parts) == 1 or not parts[1]:
        print("0\t1"); continue
    friends = {f.strip() for f in parts[1].split(',') if f.strip()}
    print(f"{len(friends)}\t1")
PY
```

**Reducer (sums counts by friend_count):**
```bash
cat > reducer_p1.py << 'PY'
#!/usr/bin/env python3
import sys
cur=None; total=0
def flush(k,v):
    if k is not None: print(f"{k}\t{v}")
for line in sys.stdin:
    line=line.strip()
    if not line: continue
    try: k,v=line.split("\t",1); v=int(v)
    except ValueError: continue
    if k==cur: total+=v
    else:
        flush(cur,total); cur=k; total=v
flush(cur,total)
PY
```

Make them executable and fix CRLF if needed:
```bash
chmod +x mapper_p1.py reducer_p1.py
dos2unix mapper_p1.py reducer_p1.py 2>/dev/null || true
```

---

## 5) Run the Hadoop Streaming job
```bash
export P1_OUT=/user/$USER/p1_output_$(date +%Y%m%d-%H%M%S)
hdfs dfs -rm -r -f "$P1_OUT" >/dev/null 2>&1 || true

mapred streaming \
  -numReduceTasks 1 \
  -file mapper_p1.py \
  -file reducer_p1.py \
  -mapper "/usr/bin/python3 mapper_p1.py" \
  -reducer "/usr/bin/python3 reducer_p1.py" \
  -input  "$P1_IN_DIR/soc-LiveJournal1Adj.txt" \
  -output "$P1_OUT"
```

Watch the logs. When it finishes you should see a line like:
```
INFO streaming.StreamJob: Output directory: /user/emdwmt/p1_output_20251001-184131
```

---

## 6) Verify output in HDFS
```bash
hdfs dfs -ls -h "$P1_OUT"
# peek the reducer output (first 20 lines)
hdfs dfs -cat "$P1_OUT"/part-* | head -n 20
```

---

## 7) Save results locally on the VM and sort

Merge HDFS output into a local file:
```bash
hdfs dfs -getmerge "$P1_OUT" ~/P1/friend_count_distribution.tsv
```

Sort numerically by friend_count (column 1):
```bash
sort -k1,1n ~/P1/friend_count_distribution.tsv > ~/P1/friend_count_distribution_sorted.tsv
```

Quick checks:
```bash
wc -l ~/P1/friend_count_distribution_sorted.tsv
head -5 ~/P1/friend_count_distribution_sorted.tsv
tail -5 ~/P1/friend_count_distribution_sorted.tsv
```

**Final local file:**  
`~/P1/friend_count_distribution_sorted.tsv`  
**Format:** `friend_count<TAB>num_users`

---

## 8) Copy final file back to your Windows machine (run on your Windows terminal)

**PowerShell:**
```powershell
scp emdwmt@cs6304-emdwmt-01.class.mst.edu:~/P1/friend_count_distribution_sorted.tsv C:\Users\emdwmt\Downloads\
```

**Git Bash:**
```bash
scp emdwmt@cs6304-emdwmt-01.class.mst.edu:~/P1/friend_count_distribution_sorted.tsv /c/Users/emdwmt/Downloads/
```

If a custom port is required (example 2222):
```powershell
scp -P 2222 emdwmt@cs6304-emdwmt-01.class.mst.edu:~/P1/friend_count_distribution_sorted.tsv C:\Users\emdwmt\Downloads\
```

---

## 9) Optional: open in Excel

Open Downloads → double-click `friend_count_distribution_sorted.tsv` (Excel will parse tabs automatically).

---

## Troubleshooting (common issues)

- **“Could not resolve hostname”** → use the exact host you use for ssh (maybe include .mst.edu) and run scp from your local machine (not the VM).
- **If you get Permission denied** → check username, SSH key, or password. Try ssh first to confirm connection.
- **NativeCodeLoader WARN in Hadoop logs** → normal on some VMs; safe to ignore.
- **If hdfs dfs -getmerge fails** → list files in `$P1_OUT` and hdfs dfs -cat the part-* files manually and redirect:
    ```bash
    hdfs dfs -cat "$P1_OUT"/part-* > ~/P1/friend_count_distribution.tsv
    ```

Run scp from your Windows machine, not from the VM, when copying from Windows → VM.  
Conversely, to fetch files from VM to Windows run scp on Windows as shown above.

---

## Save this procedure as a markdown file on the VM

If you want this exact procedure saved in `~/P1/P1_Steps.md` on the VM, run this (inside the VM):
```bash
cat > ~/P1/P1_Steps.md << 'MD'
# P1 — Step-by-step procedure
(put the full procedure text here — paste the content you want saved)
MD
```
