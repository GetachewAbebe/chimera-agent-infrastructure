export type ReviewCardProps = {
  taskId: string;
  generatedContent: string;
  confidenceScore: number;
  reasoningTrace: string;
  status: string;
  createdAt: string;
  onApprove: () => void;
  onReject: () => void;
  busy?: boolean;
};
