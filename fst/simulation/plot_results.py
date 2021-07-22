import matplotlib.pyplot as plt
import pandas as pd;

fig, axes = plt.subplots(nrows = 2, ncols = 1)

df1 = pd.read_csv('StepSFQ_baseline_timestep-1-sec.csv')
df1.set_index('t', inplace = True)
df1 = df1.groupby('name')['goodput']
ax1 = axes[0]
df1.plot(ax = ax1, legend = True)
ax1.set_xlabel('Time (s)')
ax1.set_ylabel('Goodput (tps)')
ax1.set_title('Goodput with Fairness (Stochastic Fairness Queueing)')

df2 = pd.read_csv('StepBF_baseline_timestep-1-sec.csv')
df2.set_index('t', inplace = True)
df2 = df2.groupby('name')['goodput']
ax2 = axes[1]
df2.plot(ax =ax2, legend = True)
ax2.set_xlabel('Time (s)')
ax2.set_ylabel('Goodput (tps)')
ax2.set_title('Goodput with Fairness (Bloom Filter)')

plt.tight_layout()
plt.show()
