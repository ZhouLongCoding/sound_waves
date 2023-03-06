package sound_wave;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class ContourExtraction {

	public static String[] dim_names= {"T","A","F"};
	public static String[] sum_methods= {"Ave", "Max", "Cut"};

	String contour_files_folder;
	BufferedWriter warning_messages;

	ThreeD_Data data3d;	//[num_T][num_F][num_A];

	// Frequency Domain: float[this.data3d.num_T][this.data3d.num_A]
	float[][] freq_BF;  // best freq 
	float[][] freq_BW;  // bandwidth

	// Amplitude Domain: float[this.data3d.num_T][this.data3d.num_F]
	float amp_turn_prop; // the turn proportion for DR-related properties. should be close to 1, e.g., 90%.

	float[][] amp_threshold; 
	float[][] amp_DR_x; 
	float[][] amp_DR_slope; 

	// Time Domain: float[this.data3d.num_F][this.data3d.num_A]; 
	float half_dur_proportion;
	float rs10_proportion;
	float continuous_cutoff;

	float[][] time_latency; 
	float[][] time_50duration; 
	float[][] time_rising_slope10; 

	/*
	 * Constructor. Convert the original source of 3D data into multiple (currently 8) 2D matrices,
	 * ready for plotting the contours and other 1D figures.  
	 * 
	 * The output files (in the folder "contour_files_folder" are ready for the R plotting using 
	 * fig <- plot_ly(z = as.matrix(data), y = c(...), x = c(...), type = "contour", 
	 * 		contours = list(showlabels = TRUE, coloring = 'heatmap'), line = list(smoothing = 1.5))
	 * 
	 * Note that to facilitate plotting functions, the "NaN"s in the 2D matrices will be written as "0",
	 * although we distinguish 0 and NaN in the 2D matrices for other potential analysis.
	 */

	public ContourExtraction(ThreeD_Data data_3D_source, 
			// A_domain parameters:
			float amp_turn_prop, 
			// T_domain parameters
			float half_dur_proportion,
			float rs10_proportion,
			float continuous_cutoff,
			// file/folder paths
			String warning_file, String contour_files_folder) {
		this.data3d=data_3D_source;
		this.contour_files_folder=contour_files_folder;
		try{
			BufferedWriter bw_warning = new BufferedWriter(new FileWriter(warning_file));

			// Frequency Domain
			this.freq_BF = new float[this.data3d.num_T][this.data3d.num_A];  // best freq
			BufferedWriter freq_BF_writer= new BufferedWriter(new FileWriter(this.contour_files_folder+"freq_BF.csv"));
			this.freq_BW = new float[this.data3d.num_T][this.data3d.num_A];  // bandwidth
			BufferedWriter freq_BW_writer= new BufferedWriter(new FileWriter(this.contour_files_folder+"freq_BW.csv"));
			for(int t_index=0;t_index<this.data3d.num_T;t_index++) {
				for(int A_index=0;A_index<this.data3d.num_A;A_index++) {
					//create the 1D array for summary stats calculations
					float[] fr_on_freqs=new float[this.data3d.num_F];
					for(int F_index=0;F_index<this.data3d.num_F;F_index++) {
						fr_on_freqs[F_index]=this.data3d.FireRate[t_index][F_index][A_index];
					}
					this.freq_BF[t_index][A_index]=this.freq_BF(fr_on_freqs);
					this.freq_BW[t_index][A_index]=this.freq_BW(fr_on_freqs, bw_warning);
					if(A_index<this.data3d.num_A-1) {  // write to files, converting to 0 if NaN to facilitate plotting 
						freq_BF_writer.write((Float.isNaN(this.freq_BF[t_index][A_index])?0:this.freq_BF[t_index][A_index])+",");
						freq_BW_writer.write((Float.isNaN(this.freq_BW[t_index][A_index])?0:this.freq_BW[t_index][A_index])+",");
					}else {// the last one, write "\n"
						freq_BF_writer.write((Float.isNaN(this.freq_BF[t_index][A_index])?0:this.freq_BF[t_index][A_index])+"\n");
						freq_BW_writer.write((Float.isNaN(this.freq_BW[t_index][A_index])?0:this.freq_BW[t_index][A_index])+"\n");
					}
				}
			}freq_BF_writer.close(); 
			freq_BW_writer.close();
			System.out.println("Frequency Domain DONE.");
			// Amplitude Domain
			this.amp_turn_prop=amp_turn_prop;
			this.amp_threshold = new float[this.data3d.num_T][this.data3d.num_F]; 
			BufferedWriter amp_threshold_writer= new BufferedWriter(new FileWriter(this.contour_files_folder+"amp_threshold.csv"));
			this.amp_DR_x = new float[this.data3d.num_T][this.data3d.num_F]; 
			BufferedWriter amp_DR_x_writer= new BufferedWriter(new FileWriter(this.contour_files_folder+"amp_DR_x.csv"));
			this.amp_DR_slope = new float[this.data3d.num_T][this.data3d.num_F]; 
			BufferedWriter amp_DR_slope_writer= new BufferedWriter(new FileWriter(this.contour_files_folder+"amp_DR_slope.csv"));
			for(int t_index=0;t_index<this.data3d.num_T;t_index++) {
				for(int F_index=0;F_index<this.data3d.num_F;F_index++) {
					//create the 1D array for summary stats calculations
					float[] fr_on_amps=new float[this.data3d.num_A];
					for(int A_index=0;A_index<this.data3d.num_A;A_index++) {
						fr_on_amps[A_index]=this.data3d.FireRate[t_index][F_index][A_index];
					}
					this.amp_threshold[t_index][F_index] = this.amp_threshold(fr_on_amps);
					this.amp_DR_x[t_index][F_index] = this.amp_DR(fr_on_amps, this.amp_turn_prop);
					this.amp_DR_slope[t_index][F_index]=this.amp_slopeDR(fr_on_amps, this.amp_turn_prop);
					if(F_index<this.data3d.num_F-1) {  // write to files, converting to 0 if NaN to facilitate plotting 
						amp_threshold_writer.write((Float.isNaN(this.amp_threshold[t_index][F_index])?0:this.amp_threshold[t_index][F_index])+",");
						amp_DR_x_writer.write((Float.isNaN(this.amp_DR_x[t_index][F_index])?0:this.amp_DR_x[t_index][F_index])+",");
						amp_DR_slope_writer.write((Float.isNaN(this.amp_DR_slope[t_index][F_index])?0:this.amp_DR_slope[t_index][F_index])+",");
					}else {// the last one, write "\n"
						amp_threshold_writer.write((Float.isNaN(this.amp_threshold[t_index][F_index])?0:this.amp_threshold[t_index][F_index])+"\n");
						amp_DR_x_writer.write((Float.isNaN(this.amp_DR_x[t_index][F_index])?0:this.amp_DR_x[t_index][F_index])+"\n");
						amp_DR_slope_writer.write((Float.isNaN(this.amp_DR_slope[t_index][F_index])?0:this.amp_DR_slope[t_index][F_index])+"\n");
					}
				}
			}amp_threshold_writer.close();  
			amp_DR_x_writer.close(); 
			amp_DR_slope_writer.close();
			System.out.println("Amplitude Domain DONE.");
			// Time Domain
			this.half_dur_proportion=half_dur_proportion;
			this.rs10_proportion=rs10_proportion;
			this.continuous_cutoff=continuous_cutoff;
			this.time_latency = new float[this.data3d.num_F][this.data3d.num_A];
			BufferedWriter time_latency_writer= new BufferedWriter(new FileWriter(this.contour_files_folder+"time_latency.csv"));
			this.time_50duration = new float[this.data3d.num_F][this.data3d.num_A]; 
			BufferedWriter time_50duration_writer= new BufferedWriter(new FileWriter(this.contour_files_folder+"time_50duration.csv"));
			this.time_rising_slope10 = new float[this.data3d.num_F][this.data3d.num_A];
			BufferedWriter time_rising_slope10_writer= new BufferedWriter(new FileWriter(this.contour_files_folder+"time_rising_slope10.csv"));
			for(int F_index=0;F_index<this.data3d.num_F;F_index++) {
				for(int A_index=0;A_index<this.data3d.num_A;A_index++) {
					//create the 1D array for summary stats calculations
					float[] fr_on_time=new float[this.data3d.num_T];
					for(int t_index=0;t_index<this.data3d.num_T;t_index++) {
						fr_on_time[t_index]=this.data3d.FireRate[t_index][F_index][A_index];
					}
					this.time_latency[F_index][A_index] = this.time_latency(fr_on_time);
					this.time_50duration[F_index][A_index] = this.time_50duration(fr_on_time, this.half_dur_proportion, this.continuous_cutoff, bw_warning);
					this.time_rising_slope10[F_index][A_index]=this.time_10rising_slope(fr_on_time, this.rs10_proportion, bw_warning);
					if(A_index<this.data3d.num_A-1) {  // write to files, converting to 0 if NaN to facilitate plotting 
						time_latency_writer.write((Float.isNaN(this.time_latency[F_index][A_index])?0:this.time_latency[F_index][A_index])+",");
						time_50duration_writer.write((Float.isNaN(this.time_50duration[F_index][A_index])?0:this.time_50duration[F_index][A_index])+",");
						time_rising_slope10_writer.write((Float.isNaN(this.time_rising_slope10[F_index][A_index])?0:this.time_rising_slope10[F_index][A_index])+",");
					}else {// the last one, write "\n"
						time_latency_writer.write((Float.isNaN(this.time_latency[F_index][A_index])?0:this.time_latency[F_index][A_index])+"\n");
						time_50duration_writer.write((Float.isNaN(this.time_50duration[F_index][A_index])?0:this.time_50duration[F_index][A_index])+"\n");
						time_rising_slope10_writer.write((Float.isNaN(this.time_rising_slope10[F_index][A_index])?0:this.time_rising_slope10[F_index][A_index])+"\n");
					}
				}
			}time_latency_writer.close();
			time_50duration_writer.close();
			time_rising_slope10_writer.close();
			System.out.println("Time Domain DONE.");
			bw_warning.close();
		}catch(Exception e) {e.printStackTrace();}
	}

	/*
	 * summarize a vector into a value //TODO
	 */
	public static float summarize(float[] input, String method) {
		if(method.equals("Ave")) { // average
			float sum=0;
			for(int k=0;k<input.length;k++)sum+=input[k];
			return sum/input.length;
		}else if(method.equals("Cut")) { // the cut point below which are all zeros

		}else if(method.equals("Max")) {  // the largest value

		}
		return Float.NaN;

	}

	/*
	 * Frequency Domain 1
	 * BF stands for "Best Frequency": the frequency with the highest firing rate
	 * will return NaN if the input is all-zero.
	 */
	public float freq_BF(float[] fr_on_freqs) {
		if(fr_on_freqs.length!=this.data3d.num_F) {
			System.out.println("fr_on_freqs.length!=this.data3d.num_F");
			System.exit(0);
		}
		int best_index=-1;
		float best_fr=0;
		for(int k=1;k<fr_on_freqs.length;k++) {
			if(best_fr < fr_on_freqs[k]) {
				best_fr = fr_on_freqs[k];
				best_index=k;
			}
		}
		if(best_fr==0)return Float.NaN; // no firing rate higher than 0, so no best frequency at all. 
		else return this.data3d.Frequency[best_index];
	}

	/*
	 * Frequency Domain 2
	 * BW stands for "Band Width": the frequency range that has non-zero firing rate
	 * 
	 * Assuming the data looks like 0, 0, 0, non-zero, non-zero, ..., non-zero, 0, 0, 0,
	 * this function searches for the first non-zero values from the both end as "start" 
	 * and "end", and return their difference. However, it also checks whether there are
	 * indeed 0s in between, and return an NaN if it is the case.
	 * 
	 * If end == start, we return a very small number (=0.01) to distinguish the case that
	 * no non-zero values were found. will return 0 (instead of NaN) if the input is all-zero.
	 * 
	 */
	public float freq_BW(float[] fr_on_freqs, BufferedWriter bw_warning) {
		float small_value = (float)0.01; 
		if(fr_on_freqs.length!=this.data3d.num_F) {
			System.out.println("fr_on_freqs.length!=this.data3d.num_F");
			System.exit(0);
		}
		int start=-1, end= -1;
		for(int k=0;k<fr_on_freqs.length;k++) {
			if(0 < fr_on_freqs[k]) {
				start = k;
				break;
			}
		}for(int k=fr_on_freqs.length-1;k>=0;k--) {
			if(0 < fr_on_freqs[k]) {
				end = k;
				break;
			}
		}// if not found, return 0;
		if(start==-1 || end ==-1) {  // no bandwidth at all. So it is zero (not NaN) 
			return 0;
		}else {
			if(start==end) return small_value;
			else {
				try {
					for(int k=start;k<=end;k++) {
						if(fr_on_freqs[k]<=0) {
//							bw_warning.write("Gap: freq_BW: start="+start+","+fr_on_freqs[start]+";end="+end+","+fr_on_freqs[end]+";"
//									+ "but fr_on_freqs["+k+"]="+fr_on_freqs[k]+"; Returned NaN \n");
							return Float.NaN;
						}
								
					}bw_warning.flush();
				}catch(Exception e) {e.printStackTrace();}
				return Math.abs(this.data3d.Frequency[end] - this.data3d.Frequency[start]);
			}
		}
	}

	/*
	 * Amplitude Domain 1
	 * Threshold (the lowest response amplitude) (For every frequency, T fixed)
	 * 
	 * will return NaN if the input is all-zero.
	 */
	public float amp_threshold(float[] fr_on_amps) {
		if(fr_on_amps.length!=this.data3d.num_A) {
			System.out.println("fr_on_amps.length!=this.data3d.num_A");
			System.exit(0);
		}
		int lowest_index=-1;
		for(int k=0;k<fr_on_amps.length;k++) {
			if(fr_on_amps[k]>0) {
				lowest_index=k;
				break;
			}
		}if(lowest_index!=-1) {
			return this.data3d.Amplitude[lowest_index];
		}else return Float.NaN;
	}

	/*
	 * Amplitude Domain 2
	 * Dynamic range (DR): x-axis = A, y-axis = FT, the distance between 
	 * lowest and the turn point (after which the increase of FR slows 
	 * down), which is approximately zero point of 2nd derivative)
	 * 
	 * As the number of points in the data is too few the reliably estimate
	 * the 2nd derivative, here the quick-and-dirty implementation uses the 
	 * 90% (adjustable) to max for the turn-point. 
	 */
	public float amp_DR(float[] fr_on_amps, float turn_prop) {
		if(fr_on_amps.length!=this.data3d.num_A) {
			System.out.println("fr_on_amps.length!=this.data3d.num_A");
			System.exit(0);
		}if(!(0<turn_prop && 1>turn_prop)) {
			System.out.println("turn_prop="+turn_prop+", which is not in interval (0,1), and preferably >0.9.");
			System.exit(0);
		}
		float max_fr=-1;
		int max_index=-1;
		for(int k=0;k<fr_on_amps.length;k++) {
			if(max_fr<fr_on_amps[k]) {
				max_fr=fr_on_amps[k];
				max_index=k;
			}
		}
		if(Float.compare(max_fr,0)==0) return Float.NaN;
		float turn_fr= max_fr*turn_prop;
		int turn_index=-1;
		for(int k=max_index;k>=0;k--) {
			if(turn_fr >= fr_on_amps[k]) {
				turn_index = k;
				break;
			}
		}
		int start_index=-1;
		for(int k=0;k<fr_on_amps.length;k++) {
			if(0 < fr_on_amps[k]) {
				start_index = k;
				break;
			}
		}
		if(start_index==-1 || turn_index==-1) return Float.NaN;
		return this.data3d.Amplitude[turn_index]-this.data3d.Amplitude[start_index];
	}

	/*
	 * Amplitude Domain 3
	 * Slope of Dynamic range: Slope of DR (y-axis distance / DR)
	 * 
	 *  the code is identical to the above amp_DR(float[] fr_on_amps, float turn_prop), 
	 *  except for at the end returning a ration (and checking whether the denominator is zero). 
	 */
	public float amp_slopeDR(float[] fr_on_amps, float turn_prop) {
		if(fr_on_amps.length!=this.data3d.num_A) {
			System.out.println("fr_on_amps.length!=this.data3d.num_A");
			System.exit(0);
		}if(!(0<turn_prop && 1>turn_prop)) {
			System.out.println("turn_prop="+turn_prop+", which is not in interval (0,1), and preferably >0.9.");
			System.exit(0);
		}
		float max_fr=-1;
		int max_index=-1;
		for(int k=0;k<fr_on_amps.length;k++) {
			if(max_fr<fr_on_amps[k]) {
				max_fr=fr_on_amps[k];
				max_index=k;
			}
		}
		if(Float.compare(max_fr,0)==0) return Float.NaN;
		float turn_fr= max_fr*turn_prop;
		int turn_index=-1;
		for(int k=max_index;k>=0;k--) {
			if(turn_fr >= fr_on_amps[k]) {
				turn_index = k;
				break;
			}
		}
		int start_index=-1;
		for(int k=0;k<fr_on_amps.length;k++) {
			if(0 < fr_on_amps[k]) {
				start_index = k;
				break;
			}
		}
		if(start_index==-1 || turn_index==-1 || turn_index==start_index) return Float.NaN;
		return (fr_on_amps[turn_index]-fr_on_amps[start_index])/
				(this.data3d.Amplitude[turn_index]-this.data3d.Amplitude[start_index]);
	}

	/*
	 * Time Domain 1
	 * Latency: the time point when a response starts
	 * 
	 * will return NaN if the input is all-zero.
	 */
	public float time_latency(float[] fr_on_time) {
		if(fr_on_time.length!=this.data3d.num_T) {
			System.out.println("fr_on_time.length!=this.data3d.num_T");
			System.exit(0);
		}
		for(int k=0;k<fr_on_time.length;k++) {
			if(fr_on_time[k]>0) return k;
		}return Float.NaN;
	}

	/*
	 * Time Domain 2
	 * 50% Duration (the period when firing rate is over 50% of the maximum firing rate 
	 * of each response. The percentage is flexible for adjustment)
	 * 
	 * The function assumes the duration is a continuous interval from the start_index to  
	 * end_index. It will return NaN if  there are gaps of values lower than a cutoff firing
	 * rate (specified by continuous_cutoff*percentage_max) between start_index and end_index. 
	 * 
	 * If end_index == start_index, return a very small number (=0.1) to distinguish the case that
	 * no non-zero values were found.
	 * 
	 * will return NaN if the input is all-zero. But will return 0 if no duration but max >0. (This actually
	 * won't happen as the max point by itself will return 0.1. But we keep it for double-check).
	 */
	public float time_50duration(float[] fr_on_time, float proportion, float continuous_cutoff, BufferedWriter bw_warning) {
		if(fr_on_time.length!=this.data3d.num_T) {
			System.out.println("fr_on_time.length!=this.data3d.num_T");
			System.exit(0);
		}if(!(0<proportion && 1>proportion)) {
			System.out.println("percentage="+proportion+", which is not in interval (0,1).");
			System.exit(0);
		}
		float small_value=(float)0.1;
		float max_fr=-1;
		for(int k=0;k<fr_on_time.length;k++) {
			if(max_fr<fr_on_time[k]) {
				max_fr=fr_on_time[k];
			}
		}
		if(Float.compare(max_fr,0)==0) return Float.NaN;
		float percentage_max=max_fr*proportion;
		int start_index=-1, end_index=-1;
		for(int k=0;k<fr_on_time.length;k++) {
			if(percentage_max<=fr_on_time[k]) {
				start_index=k;
				break;
			}
		}for(int k=fr_on_time.length-1;k>=0;k--) {
			if(percentage_max <= fr_on_time[k]) {
				end_index = k;
				break;
			}
		}// if not found, return 0;
		if(start_index==-1 || end_index ==-1) {  // no duration at all. So it is zero (not NaN)
			try {
				bw_warning.write("Warning: Wrong logic in time_50duration()\n");
				bw_warning.flush();
			}catch(Exception e) {e.printStackTrace();}
			return 0;
		}else {
			if(start_index==end_index) return small_value;
			else {
				try {
					for(int k=start_index;k<=end_index;k++) {
						if(fr_on_time[k]< continuous_cutoff*percentage_max) {
//							bw_warning.write("Gap: time_50duration: "
//									+ "percentage_max="+percentage_max+"; start="
//									+start_index+";end="+end_index+";but fr_on_time["+k+"]="+fr_on_time[k]+". Returned NaN\n");
							return Float.NaN;
						}
					}bw_warning.flush();
				}catch(Exception e) {e.printStackTrace();}
				return Math.abs(end_index - start_index); 
			}
		}
	}

	/*
	 * Time Domain 3
	 * Rising slope (from the 10% of the maximum firing rate to the maximum firing rate). 
	 * The 10% is adjustable as the case in time_50duration(float[] rt_on_time, float percentage).
	 * 
	 * When searching for the starting point of 10%, we start with max_index, and reduce it by 1 repetitively 
	 * until meeting the 10% -- this ensures no gap although ignored multiple peaks of 10%. 
	 * 
	 * Note that we return (max_fr - fr_on_time[start_index])/(max_index - start_index), 
	 * instead of (max_fr - percentage_max)/(max_index - start_index)
	 * 
	 * will return NaN if the input is all-zero.
	 */
	public float time_10rising_slope(float[] fr_on_time, float proportion, BufferedWriter bw_warning) {
		if(fr_on_time.length!=this.data3d.num_T) {
			System.out.println("fr_on_time.length!=this.data3d.num_T");
			System.exit(0);
		}if(!(0<proportion && 1>proportion)) {
			System.out.println("percentage="+proportion+", which is not in interval (0,1).");
			System.exit(0);
		}
		float max_fr=-1;
		int max_index=-1;
		for(int k=0;k<fr_on_time.length;k++) {
			if(max_fr<fr_on_time[k]) {
				max_fr=fr_on_time[k];
				max_index=k;
			}
		}
		if(Float.compare(max_fr,0)==0) return Float.NaN;
		float percentage_max=max_fr*proportion;
		int start_index = max_index; // start with max_index
		for(int k=max_index;k>=0;k--) {
			if(percentage_max<=fr_on_time[k]) {
				start_index=k;
			}else break; // if percentage_max > fr_on_time[k], then the last k is the right start_index
		}
		if(start_index==max_index) return Float.NaN;
		try {
			for(int k=start_index;k<=max_index;k++) {
				if(fr_on_time[k]<=percentage_max) {
					bw_warning.write("Warning: time_rising_slope: "
							+ "percentage_max="+percentage_max+"; start="
							+start_index+";max="+max_index+";but fr_on_time["+k+"]="+fr_on_time[k]+"\n");
				}
			}bw_warning.flush();
		}catch(Exception e) {e.printStackTrace();}
		return (max_fr - fr_on_time[start_index])/(max_index - start_index); 
	}

	/*
	 * output files ready for the R plotting using 
	 * fig <- plot_ly(z = data, y = c(...), x = c(...), type = "contour", 
	 * 		contours = list(showlabels = TRUE, coloring = 'heatmap'), line = list(smoothing = 1.5))
	 * 
	 */ 
	public void output4plot() {
		try {
			BufferedWriter freq_BF= new BufferedWriter(new FileWriter(this.contour_files_folder+"freq_BF.csv"));
			
		}catch(Exception e) {e.printStackTrace();}
	}
	
	public void plot_contours() {
		
	}

	public void width() {

	}

	public void slope() {

	}

	public void first_derivative() {

	}

	public void second_derivative() {

	}

	public void integral() {

	}

	public static void main(String[] args) {
		String folder= "/Users/fan/Dropbox/Zhou/ScienceFair/G_RA16/";

		String raw_txt= folder+"G_RA16-1_001Control25Jo1.raw.txt";
		String formated= folder+"test.formated.txt";
		String smoothed= folder+"test.smoothed.txt";
		String warning_file = folder+"test.warning.txt"; 
		String contour_files_folder=folder+"/contour_files/";
		int level=1;
		//		ThreeD_Data data=new ThreeD_Data(raw_txt, "byFA");
		//		data.write2file(formated);
		ThreeD_Data data_again=new ThreeD_Data(formated, "byT");
		//		data_again.write2file(smoothed);
		data_again.smoothing(level);
		data_again.write2file(smoothed);
		ThreeD_Data data_smoothed=new ThreeD_Data(smoothed, "byT");
		float amp_turn_prop=(float)0.9, half_dur_proportion=(float)0.5, rs10_proportion=(float)0.1, continuous_cutoff=(float)0.6;
		ContourExtraction extracted = new ContourExtraction(data_smoothed, amp_turn_prop, half_dur_proportion, 
				rs10_proportion, continuous_cutoff,
				warning_file, contour_files_folder);


		//		formatted_data.out2filewriting(data_smoothed);

	}
}
