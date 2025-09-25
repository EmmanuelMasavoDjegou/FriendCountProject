import java.io.IOException;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

public class FriendCountDistributionMapper extends Mapper<LongWritable, Text, IntWritable, IntWritable> {
    private final static IntWritable one = new IntWritable(1);
    private IntWritable friendCount = new IntWritable();

    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        String line = value.toString().trim();
        if (line.isEmpty()) return;

        // Split line into userID and friend list (tab OR space)
        String[] parts = line.split("\\s+", 2);
        if (parts.length < 2) {
            // user has no friends
            friendCount.set(0);
            context.write(friendCount, one);
            return;
        }

        String friendList = parts[1];
        if (friendList.isEmpty()) {
            friendCount.set(0);
        } else {
            String[] friends = friendList.split(",");
            friendCount.set(friends.length);
        }
        context.write(friendCount, one);
    }
}
